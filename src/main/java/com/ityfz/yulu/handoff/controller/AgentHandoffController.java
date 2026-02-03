package com.ityfz.yulu.handoff.controller;

import com.ityfz.yulu.common.annotation.RequireRole;
import com.ityfz.yulu.common.model.ApiResponse;
import com.ityfz.yulu.common.security.SecurityUtil;
import com.ityfz.yulu.handoff.dto.HandoffAcceptRequest;
import com.ityfz.yulu.handoff.dto.HandoffAcceptResponse;
import com.ityfz.yulu.handoff.dto.HandoffRejectRequest;
import com.ityfz.yulu.handoff.dto.HandoffRequestItemDTO;
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

}
