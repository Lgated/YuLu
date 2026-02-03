package com.ityfz.yulu.handoff.websocket.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ityfz.yulu.chat.entity.ChatSession;
import com.ityfz.yulu.chat.mapper.ChatSessionMapper;
import com.ityfz.yulu.common.enums.ErrorCodes;
import com.ityfz.yulu.common.exception.BizException;
import com.ityfz.yulu.handoff.dto.*;
import com.ityfz.yulu.handoff.entity.HandoffEvent;
import com.ityfz.yulu.handoff.entity.HandoffRequest;
import com.ityfz.yulu.handoff.enums.HandoffEventType;
import com.ityfz.yulu.handoff.enums.HandoffStatus;
import com.ityfz.yulu.handoff.enums.OperatorType;
import com.ityfz.yulu.handoff.mapper.HandoffEventMapper;
import com.ityfz.yulu.handoff.mapper.HandoffRequestMapper;
import com.ityfz.yulu.handoff.websocket.AgentWebSocketHandler;
import com.ityfz.yulu.handoff.websocket.CustomerWebSocketHandler;
import com.ityfz.yulu.ticket.entity.Ticket;
import com.ityfz.yulu.ticket.enums.TicketPriority;
import com.ityfz.yulu.ticket.enums.TicketStatus;
import com.ityfz.yulu.ticket.mapper.TicketMapper;
import com.ityfz.yulu.ticket.service.TicketService;
import com.ityfz.yulu.user.service.AgentStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 转人工服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HandoffService {

    private final ChatSessionMapper chatSessionMapper;
    private final HandoffRequestMapper handoffRequestMapper;
    private final TicketMapper ticketMapper;
    private final TicketService ticketService;
    private final HandoffQueueService handoffQueueService;
    private final HandoffEventMapper handoffEventMapper;
    private final AgentAssigner agentAssigner;
    private final AgentWebSocketHandler agentHandler;
    private final AgentStatusService agentStatusService;
    private final HandoffQueueService queueService;
    private final CustomerWebSocketHandler customerHandler;

    /**
     * 转人工申请
     */
    @Transactional
    public HandoffTransferResponse transferToAgent(Long tenantId,Long userId,Long sessionId,String reason){

        // 1、验证会话
        ChatSession chatSession = chatSessionMapper.selectById(sessionId);
        if(chatSession == null || !chatSession.getTenantId().equals(tenantId) || !chatSession.getUserId().equals(userId)){
            throw new BizException(ErrorCodes.UNAUTHORIZED,"会话不存在或无权限");
        }

        // 2、检查是否已有未完成的转人工请求
        HandoffRequest existing = handoffRequestMapper.selectUncompletedBySessionId(sessionId);
        if (existing != null && !HandoffStatus.isCompleted(existing.getStatus())){
            throw new BizException(ErrorCodes.VALIDATION_ERROR,"已有转人工请求，请勿重复申请");
        }

        // 3、检查/创建工单
        Ticket ticket = findOrCreateTicket(tenantId, userId, sessionId, reason);

        // 4. 创建转人工请求
        HandoffRequest request = new HandoffRequest();
        request.setTenantId(tenantId);
        request.setSessionId(sessionId);
        request.setUserId(userId);
        request.setTicketId(ticket.getId());
        request.setStatus(HandoffStatus.PENDING.getCode());
        request.setReason(reason);
        request.setPriority(calculatePriority(chatSession)); // 基于会话计算优先级
        handoffRequestMapper.insert(request);

        // 5. 更新会话状态
        chatSession.setChatMode("AGENT");
        chatSession.setHandoffRequestId(request.getId());
        chatSessionMapper.updateById(chatSession);

        // 6、进入排队队列
        int queuePosition = handoffQueueService.addToQueue(tenantId, request.getId());
        request.setQueuePosition(queuePosition);
        handoffRequestMapper.updateById(request);

        // 7. 记录事件
        recordEvent(request.getId(), HandoffEventType.CREATED, userId, OperatorType.USER, null);

        // 8. 异步触发智能分配
        asyncAssignAgent(tenantId, request.getId());

        // 9. 返回结果
        int estimatedWaitTime = calculateEstimatedWaitTime(tenantId, queuePosition); // 计算预计等待时间
        return HandoffTransferResponse.builder()
                .handoffRequestId(request.getId())
                .ticketId(ticket.getId())
                .queuePosition(queuePosition)
                .estimatedWaitTime(estimatedWaitTime)
                .build();

    }

    /**
     * 查找或创建工单
     */
    private Ticket findOrCreateTicket(Long tenantId, Long userId, Long sessionId, String reason) {
        Ticket existingTicket = ticketMapper.selectOne(Wrappers.<Ticket>lambdaQuery()
                .eq(Ticket::getTenantId, tenantId)
                .eq(Ticket::getUserId, userId)
                .eq(Ticket::getSessionId, sessionId)
                .in(Ticket::getStatus,
                        TicketStatus.PENDING.getCode(),
                        TicketStatus.PROCESSING.getCode())
                .last("LIMIT 1"));
        if (existingTicket != null) {
            return existingTicket;
        }

        // 没有相关工单就创建新工单
        String title = "转人工-会话#" + sessionId;
        String description = reason != null ? reason : "客户申请转人工服务";

        Ticket ticket = ticketService.createTicketOnNegative(
                tenantId, userId, sessionId, description, TicketPriority.MEDIUM.getCode());

        log.info("[HandoffService] 创建工单：ticketId={}, sessionId={}", ticket.getId(), sessionId);
        return ticket;
    }

    /**
     * 计算优先级（基于会话信息）
     */
    private String calculatePriority(ChatSession session) {
        // TODO: 简化处理，可以根据会话时长、消息数量等计算
        return TicketPriority.MEDIUM.getCode();
    }


    /**
     * 记录事件
     */
    private void recordEvent(Long handoffRequestId, HandoffEventType eventType, Long operatorId,OperatorType operatorType, Map<String, Object> eventData) {
        HandoffRequest request = handoffRequestMapper.selectById(handoffRequestId);
        if (request == null) {
            return;
        }

        HandoffEvent event = new HandoffEvent();
        event.setTenantId(request.getTenantId());
        event.setHandoffRequestId(handoffRequestId);
        event.setEventType(eventType.getCode());
        event.setOperatorId(operatorId);
        event.setOperatorType(operatorType.getCode());

        if (eventData != null) {
            event.setEventData(JSON.toJSONString(eventData));
        }

        handoffEventMapper.insert(event);
    }

    /**
     * 计算预计等待时间（秒）
     */
    private int calculateEstimatedWaitTime(Long tenantId, int queuePosition) {
        // TODO: 简化处理：假设每个请求平均处理时间30秒
        int avgProcessTime = 30;
        return queuePosition * avgProcessTime;
    }

    /**
     * 异步分配客服
     */
    @Async
    private void asyncAssignAgent(Long tenantId, Long handoffRequestId) {
        try {
            Thread.sleep(1000); // 延迟1秒，确保请求已保存

            Long agentId = agentAssigner.assignAgent(tenantId, handoffRequestId);
            if (agentId != null) {
                // 分配成功，更新请求状态
                HandoffRequest request = handoffRequestMapper.selectById(handoffRequestId);
                request.setAgentId(agentId);
                request.setStatus(HandoffStatus.ASSIGNED.getCode());
                request.setAssignedAt(LocalDateTime.now());
                handoffRequestMapper.updateById(request);

                // 记录事件
                recordEvent(handoffRequestId, HandoffEventType.ASSIGNED, agentId, OperatorType.SYSTEM, null);

                // WebSocket推送通知给客服
                sendHandoffRequestNotification(tenantId, agentId, request);
            }
        } catch (Exception e) {
            log.error("[HandoffService] 异步分配客服失败：handoffRequestId={}", handoffRequestId, e);
        }
    }

    /**
     * 发送转人工请求通知给客服
     */
    private void sendHandoffRequestNotification(Long tenantId, Long agentId, HandoffRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("handoffRequestId", request.getId());
        payload.put("sessionId", request.getSessionId());
        payload.put("userId", request.getUserId());
        payload.put("ticketId", request.getTicketId());
        payload.put("priority", request.getPriority());
        payload.put("reason", request.getReason());
        payload.put("queuePosition", request.getQueuePosition());

        WebSocketMessage message = WebSocketMessage.builder()
                .type("HANDOFF_REQUEST")
                .payload(payload)
                .timestamp(LocalDateTime.now().toString())
                .build();

        agentHandler.sendToAgent(tenantId, agentId, message);
        log.info("[HandoffService] 转人工请求通知已发送：agentId={}, handoffRequestId={}", agentId, request.getId());
    }


    /**
     * 客服接受转人工请求
     */
    @Transactional
    public HandoffAcceptResponse acceptHandoff(Long tenantId, Long agentId, Long handoffRequestId) {
        // 1. 验证请求
        HandoffRequest request = handoffRequestMapper.selectById(handoffRequestId);
        if (request == null || !request.getTenantId().equals(tenantId)) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "转人工请求不存在");
        }

        if (!agentId.equals(request.getAgentId())) {
            throw new BizException(ErrorCodes.FORBIDDEN, "无权限接受此转人工请求");
        }

        if (!HandoffStatus.ASSIGNED.getCode().equals(request.getStatus())) {
            throw new BizException(ErrorCodes.VALIDATION_ERROR, "转人工请求状态不正确");
        }

        // 2. 检查是否可接入
        if (!agentStatusService.canAcceptSession(tenantId, agentId)) {
            throw new BizException(ErrorCodes.VALIDATION_ERROR, "当前负载已满，无法接入");
        }

        // 3. 更新请求状态
        request.setStatus(HandoffStatus.ACCEPTED.getCode());
        request.setAcceptedAt(LocalDateTime.now());
        handoffRequestMapper.updateById(request);

        // 4. 更新会话
        ChatSession session = chatSessionMapper.selectById(request.getSessionId());
        session.setAgentId(agentId);
        session.setChatMode("AGENT");
        chatSessionMapper.updateById(session);

        // 5. 更新工单状态
        Ticket ticket = ticketMapper.selectById(request.getTicketId());
        ticket.setStatus(TicketStatus.PROCESSING.getCode());
        ticket.setAssignee(agentId);
        ticketMapper.updateById(ticket);

        // 6. 增加客服会话数
        agentStatusService.incrementSessionCount(tenantId, agentId);

        // 7. 从排队队列移除
        queueService.removeFromQueue(tenantId, handoffRequestId);

        // 8. 记录事件
        recordEvent(handoffRequestId, HandoffEventType.ACCEPTED, agentId, OperatorType.AGENT, null);

        // 9. WebSocket通知客户
        sendHandoffAcceptedNotification(tenantId, request.getUserId(), request.getSessionId(), agentId);

        return HandoffAcceptResponse.builder()
                .handoffRequestId(handoffRequestId)
                .sessionId(request.getSessionId())
                .userId(request.getUserId())
                .ticketId(request.getTicketId())
                .build();
    }

    /**
     * 发送客服已接受通知给客户
     */
    private void sendHandoffAcceptedNotification(Long tenantId, Long userId, Long sessionId, Long agentId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("agentId", agentId);

        WebSocketMessage message = WebSocketMessage.builder()
                .type("HANDOFF_ACCEPTED")
                .payload(payload)
                .timestamp(LocalDateTime.now().toString())
                .build();

        customerHandler.sendToCustomer(tenantId, userId, sessionId, message);
        log.info("[HandoffService] 客服已接受通知已发送：userId={}, sessionId={}, agentId={}", userId, sessionId, agentId);
    }

    /**
     * 查询转人工状态
     */
    public HandoffStatusResponse getHandoffStatus(Long tenantId, Long userId, Long handoffRequestId) {

        HandoffRequest request = handoffRequestMapper.selectById(handoffRequestId);
        if (request == null || !request.getTenantId().equals(tenantId) || !request.getUserId().equals(userId)) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "转人工请求不存在或无权限");
        }

        // 更新排队位置
        int queuePosition = queueService.getQueuePosition(tenantId, handoffRequestId);
        if (queuePosition > 0 && request.getQueuePosition() != queuePosition) {
            request.setQueuePosition(queuePosition);
            handoffRequestMapper.updateById(request);
        }

        String agentName = null;
        if (request.getAgentId() != null) {
            // TODO: 获取客服名称（这里简化，实际可以从User表查询）
            agentName = "客服#" + request.getAgentId();
        }

        int estimatedWaitTime = calculateEstimatedWaitTime(tenantId, queuePosition);

        return HandoffStatusResponse.builder()
                .handoffRequestId(handoffRequestId)
                .status(request.getStatus())
                .queuePosition(queuePosition > 0 ? queuePosition : null)
                .estimatedWaitTime(estimatedWaitTime)
                .assignedAgentId(request.getAgentId())
                .assignedAgentName(agentName)
                .build();
    }

    /**
     * 获取客服待处理的转人工请求列表
     */
    public List<HandoffRequestItemDTO> getPendingHandoffRequests(Long tenantId, Long agentId) {
        List<HandoffRequest> requests = handoffRequestMapper.selectPendingByAgentId(tenantId, agentId);

        return requests.stream().map(request -> {
            // 这里简化，实际可以从User表查询客户名称
            String userName = "客户#" + request.getUserId();

            // 获取工单标题
            String ticketTitle = "转人工-会话#" + request.getSessionId();
            if (request.getTicketId() != null) {
                Ticket ticket = ticketMapper.selectById(request.getTicketId());
                if (ticket != null) {
                    ticketTitle = ticket.getTitle();
                }
            }

            return HandoffRequestItemDTO.builder()
                    .handoffRequestId(request.getId())
                    .sessionId(request.getSessionId())
                    .userId(request.getUserId())
                    .userName(userName)
                    .ticketId(request.getTicketId())
                    .ticketTitle(ticketTitle)
                    .priority(request.getPriority())
                    .reason(request.getReason())
                    .queuePosition(request.getQueuePosition())
                    .createdAt(request.getCreateTime())
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * 客户取消转人工请求
     */
    @Transactional
    public void cancel(Long tenantId, Long userId, Long handoffRequestId) {
        // 1. 查询转人工请求
        HandoffRequest request = handoffRequestMapper.selectById(handoffRequestId);
        if (request == null || !request.getTenantId().equals(tenantId) || !request.getUserId().equals(userId)) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "转人工请求不存在或无权限");
        }

        // 2. 检查状态：只有PENDING或ASSIGNED状态可以取消
        if (!HandoffStatus.PENDING.getCode().equals(request.getStatus()) &&
                !HandoffStatus.ASSIGNED.getCode().equals(request.getStatus())) {
            throw new BizException(ErrorCodes.VALIDATION_ERROR, "当前状态不允许取消");
        }

        // 3. 更新状态
        request.setStatus(HandoffStatus.CANCELLED.getCode());
        request.setClosedAt(LocalDateTime.now());
        handoffRequestMapper.updateById(request);

        // 4. 从排队队列中移除
        queueService.removeFromQueue(tenantId, handoffRequestId);

        // 5. 记录事件
        recordEvent(handoffRequestId, HandoffEventType.CANCELLED, userId, OperatorType.USER, null);

        // 6. 如果已分配客服，发送通知
        if (request.getAgentId() != null) {
            sendCancellationNotification(tenantId, request.getAgentId(), handoffRequestId);
        }

        log.info("[HandoffService] 转人工请求已取消：handoffRequestId={}, userId={}", handoffRequestId, userId);
    }

    /**
     * 发送取消通知给客服
     */
    private void sendCancellationNotification(Long tenantId, Long agentId, Long handoffRequestId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("handoffRequestId", handoffRequestId);
        payload.put("cancelledAt", LocalDateTime.now().toString());

        WebSocketMessage message = WebSocketMessage.builder()
                .type("HANDOFF_CANCELLED")
                .payload(payload)
                .timestamp(LocalDateTime.now().toString())
                .build();

        // 发送通知给客服
        agentHandler.sendToAgent(tenantId, agentId, message);
    }


    /**
     * 客服拒绝转人工请求
     */
    @Transactional
    public void decline(Long tenantId, Long agentId, Long handoffRequestId, String reason) {
        // 1. 验证请求
        HandoffRequest request = handoffRequestMapper.selectById(handoffRequestId);
        if (request == null || !request.getTenantId().equals(tenantId)) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "转人工请求不存在");
        }
        if (!agentId.equals(request.getAgentId())) {
            throw new BizException(ErrorCodes.FORBIDDEN, "无权限拒绝此转人工请求");
        }
        if (!HandoffStatus.ASSIGNED.getCode().equals(request.getStatus())) {
            throw new BizException(ErrorCodes.VALIDATION_ERROR, "当前状态无法拒绝");
        }

        // 2. 记录拒绝事件
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("reason", reason);
        recordEvent(handoffRequestId, HandoffEventType.REJECTED, agentId, OperatorType.AGENT, eventData);

        // 3. 将请求状态重置为 PENDING，并清空分配信息，以便重新分配
        request.setStatus(HandoffStatus.PENDING.getCode());
        request.setAgentId(null);
        request.setAssignedAt(null);
        request.setRejectReason(reason); // 记录本次拒绝原因
        handoffRequestMapper.updateById(request);

        // 4. 将请求重新放入队列头部，优先处理
        // Note: A more robust implementation might use a separate re-queue mechanism
        // or penalize the agent who rejected. For simplicity, we re-queue it.
        queueService.addToQueue(tenantId, handoffRequestId); // This adds to the end, for priority, you might need a different queue method.

        // 5. 再次触发异步分配
        asyncAssignAgent(tenantId, handoffRequestId);

        log.info("[HandoffService] 客服已拒绝转人工请求，已重新入队: handoffRequestId={}, agentId={}", handoffRequestId, agentId);
    }

    /**
     * 客服完成转人工对话
     */
    @Transactional
    public void complete(Long tenantId, Long agentId, Long handoffRequestId) {
        // 1. 验证请求
        HandoffRequest request = handoffRequestMapper.selectById(handoffRequestId);
        if (request == null || !request.getTenantId().equals(tenantId)) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "转人工请求不存在");
        }
        if (!agentId.equals(request.getAgentId())) {
            throw new BizException(ErrorCodes.FORBIDDEN, "无权限完成此对话");
        }
        // Allow completing from ACCEPTED or IN_PROGRESS states
        if (!HandoffStatus.ACCEPTED.getCode().equals(request.getStatus()) &&
                !HandoffStatus.IN_PROGRESS.getCode().equals(request.getStatus())) {
            throw new BizException(ErrorCodes.VALIDATION_ERROR, "当前状态无法完成");
        }

        // 2. 更新请求状态
        request.setStatus(HandoffStatus.COMPLETED.getCode());
        request.setCompletedAt(LocalDateTime.now());
        handoffRequestMapper.updateById(request);

        // 3. 更新会话模式（可选，可以将会话模式改回AI）
        ChatSession session = chatSessionMapper.selectById(request.getSessionId());
        if (session != null) {
            session.setChatMode("AI"); // Or keep it as AGENT but note the handoff is complete
            chatSessionMapper.updateById(session);
        }

        // 4. 更新关联工单状态为 DONE
        if (request.getTicketId() != null) {
            Ticket ticket = ticketMapper.selectById(request.getTicketId());
            if (ticket != null && TicketStatus.PROCESSING.getCode().equals(ticket.getStatus())) {
                ticket.setStatus(TicketStatus.DONE.getCode());
                ticketMapper.updateById(ticket);
            }
        }

        // 5. 减少客服当前会话数
        agentStatusService.decrementSessionCount(tenantId, agentId);

        // 6. 记录事件
        recordEvent(handoffRequestId, HandoffEventType.COMPLETED, agentId, OperatorType.AGENT, null);

        // 7. WebSocket通知客户对话已结束
        sendCompletionNotification(tenantId, request.getUserId(), request.getSessionId());

        log.info("[HandoffService] 转人工对话已完成: handoffRequestId={}, agentId={}", handoffRequestId, agentId);
    }

    /**
     * 发送对话完成通知给客户
     */
    private void sendCompletionNotification(Long tenantId, Long userId, Long sessionId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("message", "本次人工服务已结束，感谢您的咨询。");

        WebSocketMessage message = WebSocketMessage.builder()
                .type("HANDOFF_COMPLETED")
                .payload(payload)
                .timestamp(LocalDateTime.now().toString())
                .build();

        customerHandler.sendToCustomer(tenantId, userId, sessionId, message);
    }
}
