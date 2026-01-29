package com.ityfz.yulu.handoff.websocket.service;

import com.ityfz.yulu.chat.entity.ChatMessage;
import com.ityfz.yulu.chat.entity.ChatSession;
import com.ityfz.yulu.chat.mapper.ChatMessageMapper;
import com.ityfz.yulu.chat.mapper.ChatSessionMapper;
import com.ityfz.yulu.common.enums.ErrorCodes;
import com.ityfz.yulu.common.exception.BizException;
import com.ityfz.yulu.handoff.dto.WebSocketMessage;
import com.ityfz.yulu.handoff.websocket.AgentWebSocketHandler;
import com.ityfz.yulu.handoff.websocket.CustomerWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * WebSocket消息服务
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WebSocketMessageService {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final CustomerWebSocketHandler customerHandler;
    private final AgentWebSocketHandler agentHandler;

    /**
     * 处理客户发送的消息
     */
    @Transactional
    public void handleCustomerMessage(Long tenantId, Long userId, Long sessionId, WebSocketMessage wsMessage) {
        // 1、 验证会话
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if(session == null || !session.getTenantId().equals(tenantId) || !session.getUserId().equals(userId)){
            throw new BizException(ErrorCodes.UNAUTHORIZED,"会话不存在或无权限");
        }
        // 2、验证是否已接入客服
        if (session.getAgentId() == null || !"AGENT".equals(session.getChatMode())){
            throw new BizException(ErrorCodes.VALIDATION_ERROR,"当前会话未接入客服");
        }
        // 3、处理不同类型的消息
        String messageType = wsMessage.getType();
        Map<String, Object> payload = wsMessage.getPayload();
        if ("TEXT".equals(messageType)) {
            // 文本消息
            String content = (String) payload.get("content");
            if (content == null || content.trim().isEmpty()) {
                throw new BizException(ErrorCodes.VALIDATION_ERROR, "消息内容不能为空");
            }

            // 保存消息到数据库
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setTenantId(tenantId);
            chatMessage.setSessionId(sessionId);
            chatMessage.setSenderType("USER");
            chatMessage.setContent(content);
            chatMessage.setCreateTime(LocalDateTime.now());
            chatMessageMapper.insert(chatMessage);

            // 转发到客服
            WebSocketMessage forwardMessage = WebSocketMessage.builder()
                    .type("Text")
                    .payload(Map.of(
                            "messageId", chatMessage.getId(),
                            "sessionId", sessionId,
                            "content", content,
                            "senderType", "USER"
                    ))
                    .timestamp(LocalDateTime.now().toString())
                    .build();
            agentHandler.sendToAgent(tenantId, session.getAgentId(), forwardMessage);
            log.info("[WebSocket] 客户消息已转发给客服：sessionId={}, agentId={}", sessionId, session.getAgentId());
        }else if ("TYPING".equals(messageType)) {
            // 正在输入提示（转发给客服）
            WebSocketMessage typingMessage = WebSocketMessage.builder()
                    .type("TYPING")
                    .payload(Map.of("sessionId", sessionId))
                    .timestamp(LocalDateTime.now().toString())
                    .build();
            agentHandler.sendToAgent(tenantId, session.getAgentId(), typingMessage);
        }

    }

    /**
     * 处理客服发送的消息
     */
    @Transactional
    public void handleAgentMessage(Long tenantId, Long agentId, WebSocketMessage wsMessage) {
        Map<String, Object> payload = wsMessage.getPayload();
        Long sessionId = Long.valueOf(payload.get("sessionId").toString());

        // 1. 验证会话
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null || !session.getTenantId().equals(tenantId)) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "会话不存在或无权限");
        }

        // 2. 验证客服权限
        if (!agentId.equals(session.getAgentId())) {
            throw new BizException(ErrorCodes.FORBIDDEN, "无权限操作此会话");
        }

        // 3. 处理不同类型的消息
        String messageType = wsMessage.getType();

        if ("TEXT".equals(messageType)) {
            // 文本消息
            String content = (String) payload.get("content");
            if (content == null || content.trim().isEmpty()) {
                throw new BizException(ErrorCodes.VALIDATION_ERROR, "消息内容不能为空");
            }

            // 保存消息到数据库
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setTenantId(tenantId);
            chatMessage.setSessionId(sessionId);
            chatMessage.setSenderType("AGENT");
            chatMessage.setContent(content);
            chatMessage.setCreateTime(LocalDateTime.now());
            chatMessageMapper.insert(chatMessage);

            // 转发给客户
            WebSocketMessage forwardMessage = WebSocketMessage.builder()
                    .type("TEXT")
                    .payload(Map.of(
                            "messageId", chatMessage.getId(),
                            "sessionId", sessionId,
                            "content", content,
                            "senderType", "AGENT"
                    ))
                    .timestamp(LocalDateTime.now().toString())
                    .build();
            // 发送到客户
            customerHandler.sendToCustomer(tenantId, session.getUserId(), sessionId, forwardMessage);

            log.info("[WebSocket] 客服消息已转发给客户：sessionId={}, userId={}", sessionId, session.getUserId());
        } else if ("TYPING".equals(messageType)) {
            // 正在输入提示（转发给客户）
            WebSocketMessage typingMessage = WebSocketMessage.builder()
                    .type("TYPING")
                    .payload(Map.of("sessionId", sessionId))
                    .timestamp(LocalDateTime.now().toString())
                    .build();
            customerHandler.sendToCustomer(tenantId, session.getUserId(), sessionId, typingMessage);
        }
    }

}
