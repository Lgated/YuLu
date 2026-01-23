package com.ityfz.yulu.admin.controller;

import com.ityfz.yulu.common.annotation.RequireRole;
import com.ityfz.yulu.common.enums.ErrorCodes;
import com.ityfz.yulu.common.exception.BizException;
import com.ityfz.yulu.common.model.ApiResponse;
import com.ityfz.yulu.common.security.SecurityUtil;
import com.ityfz.yulu.user.service.AgentStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/user")
@RequiredArgsConstructor
public class AdminUserController {

    private final AgentStatusService agentStatusService;

    /**
     * 更新在线状态（客服使用）
     * PUT /api/admin/user/online-status
     */
    @PutMapping("/online-status")
    @RequireRole({"ADMIN", "AGENT"})
    public ApiResponse<Void> updateOnlineStatus(@RequestParam String status) {
        Long userId = SecurityUtil.currentUserId();
        Long tenantId = SecurityUtil.currentTenantId();
        if (userId == null) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "请先登录");
        }

        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "缺少租户信息");
        }
        // status: ONLINE/OFFLINE/AWAY
        if ("ONLINE".equalsIgnoreCase(status)) {
            agentStatusService.setOnline(tenantId, userId);
        } else if ("OFFLINE".equalsIgnoreCase(status)) {
            agentStatusService.setOffline(tenantId, userId);
        } else if ("AWAY".equalsIgnoreCase(status)) {
            agentStatusService.setAway(tenantId, userId);
        } else {
            throw new BizException(ErrorCodes.VALIDATION_ERROR, "状态值无效");
        }
        return ApiResponse.success("状态更新成功");
    }
}
