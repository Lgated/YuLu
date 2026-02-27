package com.ityfz.yulu.handoff.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ityfz.yulu.chat.entity.ChatMessage;
import com.ityfz.yulu.chat.mapper.ChatMessageMapper;
import com.ityfz.yulu.common.annotation.RequireRole;
import com.ityfz.yulu.common.enums.ErrorCodes;
import com.ityfz.yulu.common.exception.BizException;
import com.ityfz.yulu.common.model.ApiResponse;
import com.ityfz.yulu.common.security.SecurityUtil;
import com.ityfz.yulu.handoff.dto.HandoffAcceptRequest;
import com.ityfz.yulu.handoff.dto.HandoffAcceptResponse;
import com.ityfz.yulu.handoff.dto.HandoffRejectRequest;
import com.ityfz.yulu.handoff.dto.HandoffRequestItemDTO;
import com.ityfz.yulu.handoff.entity.HandoffRequest;
import com.ityfz.yulu.handoff.enums.HandoffStatus;
import com.ityfz.yulu.handoff.mapper.HandoffRequestMapper;
import com.ityfz.yulu.handoff.websocket.service.HandoffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/agent/handoff")
@RequiredArgsConstructor
@Validated
@RequireRole({"AGENT", "ADMIN"}) // 客服或管理员可访问
@Tag(name = "B端-转人工（Handoff）", description = "客服获取待处理请求、接受、拒绝、完成")
public class AgentHandoffController {

    private final HandoffService handoffService;
    private final HandoffRequestMapper handoffRequestMapper;
    private final ChatMessageMapper chatMessageMapper;

    @GetMapping("/pending")
    @Operation(summary = "获取待处理的转人工请求", description = "获取已分配给当前客服但尚未接受的请求列表")
    public ApiResponse<List<HandoffRequestItemDTO>> getPendingHandoffRequests() {
        Long tenantId = SecurityUtil.currentTenantId();
        Long agentId = SecurityUtil.currentUserId();
        List<HandoffRequestItemDTO> response = handoffService.getPendingHandoffRequests(tenantId, agentId);
        return ApiResponse.success("查询成功", response);
    }

    @PostMapping("/accept")
    @Operation(summary = "接受转人工请求", description = "客服接受一个转人工请求，并准备开始对话")
    public ApiResponse<HandoffAcceptResponse> acceptHandoff(@Valid @RequestBody HandoffAcceptRequest request) {
        Long tenantId = SecurityUtil.currentTenantId();
        Long agentId = SecurityUtil.currentUserId();
        HandoffAcceptResponse response = handoffService.acceptHandoff(tenantId, agentId, request.getHandoffRequestId());
        return ApiResponse.success("已接受转人工请求", response);
    }

    @PostMapping("/reject")
    @Operation(summary = "拒绝转人工请求", description = "客服拒绝一个转人工请求，请求将被重新分配或超时")
    public ApiResponse<Void> rejectHandoff(@Valid @RequestBody HandoffRejectRequest request) {
        Long tenantId = SecurityUtil.currentTenantId();
        Long agentId = SecurityUtil.currentUserId();
        handoffService.decline(tenantId, agentId, request.getHandoffRequestId(), request.getReason());
        return ApiResponse.success("已拒绝转人工请求");
    }

    @PostMapping("/complete/{handoffRequestId}")
    @Operation(summary = "完成转人工对话", description = "客服标记一个转人工会话已完成")
    public ApiResponse<Void> completeHandoff(@PathVariable Long handoffRequestId) {
        Long tenantId = SecurityUtil.currentTenantId();
        Long agentId = SecurityUtil.currentUserId();
        handoffService.complete(tenantId, agentId, handoffRequestId);
        return ApiResponse.success("对话已完成");
    }

    @GetMapping("/by-session/{sessionId}")
    @Operation(summary = "根据会话ID获取转人工请求", description = "获取指定会话的转人工请求信息")
    public ApiResponse<HandoffRequestItemDTO> getBySessionId(@PathVariable Long sessionId) {
        Long tenantId = SecurityUtil.currentTenantId();
        Long agentId = SecurityUtil.currentUserId();
        
        // 查询该会话的转人工请求
        HandoffRequest request = handoffRequestMapper.selectOne(Wrappers.<HandoffRequest>lambdaQuery()
            .eq(HandoffRequest::getTenantId, tenantId)
            .eq(HandoffRequest::getSessionId, sessionId)
            .eq(HandoffRequest::getAgentId, agentId)
            .in(HandoffRequest::getStatus, 
                HandoffStatus.ACCEPTED.getCode(), 
                HandoffStatus.IN_PROGRESS.getCode())
            .orderByDesc(HandoffRequest::getCreateTime)
            .last("LIMIT 1"));
        
        if (request == null) {
            throw new BizException(ErrorCodes.NOT_FOUND, "未找到该会话的转人工请求");
        }
        ChatMessage latestUserMsg = chatMessageMapper.selectOne(
                Wrappers.<ChatMessage>lambdaQuery()
                        .eq(ChatMessage::getTenantId, tenantId)
                        .eq(ChatMessage::getSessionId, sessionId)
                        .eq(ChatMessage::getSenderType, "USER")
                        .orderByDesc(ChatMessage::getCreateTime)
                        .last("LIMIT 1")
        );

        String latestEmotion = (latestUserMsg == null || latestUserMsg.getEmotion() == null)
                ? "NEUTRAL" : latestUserMsg.getEmotion().toUpperCase();
        String latestIntent = (latestUserMsg == null || latestUserMsg.getIntent() == null)
                ? "GENERAL" : latestUserMsg.getIntent().toUpperCase();
        String riskLevel = calcRisk(latestEmotion, latestIntent);
        
        // 构建返回对象
        HandoffRequestItemDTO dto = HandoffRequestItemDTO.builder()
            .handoffRequestId(request.getId())
            .sessionId(request.getSessionId())
            .userId(request.getUserId())
            .userName("客户#" + request.getUserId())
            .ticketId(request.getTicketId())
            .priority(request.getPriority())
            .latestEmotion(latestEmotion)
            .latestIntent(latestIntent)
            .riskLevel(riskLevel)
            .reason(request.getReason())
            .createdAt(request.getCreateTime())
            .build();
        
        return ApiResponse.success("查询成功", dto);
    }

    private String calcRisk(String emotion, String intent) {
        String e = emotion == null ? "" : emotion.toUpperCase();
        String i = intent == null ? "" : intent.toUpperCase();
        if (("ANGRY".equals(e) || "NEGATIVE".equals(e)) && "COMPLAIN".equals(i)) return "HIGH";
        if ("ANGRY".equals(e) || "NEGATIVE".equals(e) || "COMPLAIN".equals(i)) return "MEDIUM";
        return "LOW";
    }

}
