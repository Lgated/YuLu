package com.ityfz.yulu.handoff.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ityfz.yulu.common.annotation.RequireRole;
import com.ityfz.yulu.common.model.ApiResponse;
import com.ityfz.yulu.common.security.SecurityUtil;
import com.ityfz.yulu.handoff.dto.AdminNotifyDTO;
import com.ityfz.yulu.handoff.dto.ForceAgentStatusDTO;
import com.ityfz.yulu.handoff.dto.HandoffRecordQueryDTO;
import com.ityfz.yulu.handoff.service.AdminAgentManagementService;
import com.ityfz.yulu.handoff.service.AdminHandoffService;
import com.ityfz.yulu.handoff.vo.AgentMonitorVO;
import com.ityfz.yulu.handoff.vo.HandoffRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/handoff")
@RequiredArgsConstructor
@Validated
@RequireRole("ADMIN")
public class AdminHandoffController {


    private final AdminHandoffService adminHandoffService;
    private final AdminAgentManagementService adminAgentService;

    @GetMapping("/records")
    public ApiResponse<Page<HandoffRecordVO>> getRecords(HandoffRecordQueryDTO query) {
        Long tenantId = SecurityUtil.currentTenantId();
        return ApiResponse.success(adminHandoffService.queryRecords(tenantId, query));
    }

    @GetMapping("/agent/status")
    public ApiResponse<List<AgentMonitorVO>> getAgentStatus() {
        Long tenantId = SecurityUtil.currentTenantId();
        return ApiResponse.success(adminAgentService.getAgentMonitorList(tenantId));
    }

    @PostMapping("/agent/{agentId}/status")
    public ApiResponse<Void> forceStatus(@PathVariable Long agentId, @RequestBody ForceAgentStatusDTO dto) {
        Long tenantId = SecurityUtil.currentTenantId();
        adminAgentService.forceUpdateAgentStatus(tenantId, agentId, dto.getStatus());
        return ApiResponse.success("状态已强制更新");
    }

    @PostMapping("/notify")
    public ApiResponse<Void> sendNotify(@RequestBody AdminNotifyDTO dto) {
        Long tenantId = SecurityUtil.currentTenantId();
        Long userId = SecurityUtil.currentUserId();
        adminAgentService.broadcastNotification(tenantId, userId, dto.getTitle(), dto.getContent());
        return ApiResponse.success("通知已下发");
    }
}
