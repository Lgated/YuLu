package com.ityfz.yulu.handoff.controller;

import com.ityfz.yulu.common.annotation.RequireRole;
import com.ityfz.yulu.common.model.ApiResponse;
import com.ityfz.yulu.common.security.SecurityUtil;
import com.ityfz.yulu.handoff.dto.EndHandoffByUserRequest;
import com.ityfz.yulu.handoff.dto.HandoffStatusResponse;
import com.ityfz.yulu.handoff.dto.HandoffTransferRequest;
import com.ityfz.yulu.handoff.dto.HandoffTransferResponse;
import com.ityfz.yulu.handoff.websocket.service.HandoffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/customer/handoff")
@RequiredArgsConstructor
@Validated
@RequireRole("USER") // 仅客户可访问
@Tag(name = "C端-转人工（Handoff）", description = "客户申请转人工、查询状态、取消")
public class CustomerHandoffController {

    private final HandoffService handoffService;

    @PostMapping("/transfer")
    @Operation(summary = "申请转人工", description = "客户为指定会话申请转人工服务")
    public ApiResponse<HandoffTransferResponse> transferToAgent(@Valid @RequestBody HandoffTransferRequest request) {
        Long tenantId = SecurityUtil.currentTenantId();
        Long userId = SecurityUtil.currentUserId();
        HandoffTransferResponse response = handoffService.transferToAgent(tenantId, userId, request.getSessionId(), request.getReason());
        return ApiResponse.success("转人工申请已提交", response);
    }

    @GetMapping("/status/{handoffRequestId}")
    @Operation(summary = "查询转人工状态", description = "客户轮询查询转人工请求的当前状态")
    public ApiResponse<HandoffStatusResponse> getHandoffStatus(@PathVariable Long handoffRequestId) {
        Long tenantId = SecurityUtil.currentTenantId();
        Long userId = SecurityUtil.currentUserId();
        HandoffStatusResponse response = handoffService.getHandoffStatus(tenantId, userId, handoffRequestId);
        return ApiResponse.success("查询成功", response);
    }

    @PostMapping("/cancel/{handoffRequestId}")
    @Operation(summary = "取消转人工", description = "客户在客服接入前取消转人工请求")
    public ApiResponse<Void> cancelHandoff(@PathVariable Long handoffRequestId) {
        Long tenantId = SecurityUtil.currentTenantId();
        Long userId = SecurityUtil.currentUserId();
        handoffService.cancel(tenantId, userId, handoffRequestId);
        return ApiResponse.success("已取消转人工申请");
    }

    /**
     * 用户结束对话
     */
    @PostMapping("/end-by-user")
    @ResponseBody
    public ApiResponse<Void> endByUser(@RequestBody EndHandoffByUserRequest request) {
        Long tenantId = SecurityUtil.currentTenantId();
        Long userId = SecurityUtil.currentUserId();

        handoffService.endByUser(tenantId, userId, request.getHandoffRequestId());
        return ApiResponse.success("对话已结束");
    }

}
