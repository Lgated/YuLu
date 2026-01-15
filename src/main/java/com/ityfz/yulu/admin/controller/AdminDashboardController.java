package com.ityfz.yulu.admin.controller;

import com.ityfz.yulu.admin.dto.DashboardStats;
import com.ityfz.yulu.common.annotation.RequireRole;
import com.ityfz.yulu.common.enums.ErrorCodes;
import com.ityfz.yulu.common.exception.BizException;
import com.ityfz.yulu.common.model.ApiResponse;
import com.ityfz.yulu.common.security.SecurityUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * B端数据看板Controller
 * 权限要求：ADMIN 或 AGENT
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@RequireRole({"ADMIN", "AGENT"})
public class AdminDashboardController {


    /**
     * 获取统计数据
     * GET /api/admin/dashboard/stats
     */
    @GetMapping("/stats")
    public ApiResponse<DashboardStats> getStats() {
        Long tenantId = SecurityUtil.currentTenantId();
        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "缺少租户信息，请先登录");
        }

        // TODO: 实现统计数据查询
        // 1. 今日对话量
        // 2. AI解决率
        // 3. 转人工率
        // 4. 工单处理量
        // 5. 平均响应时间
        // 6. 客户满意度

        DashboardStats stats = new DashboardStats();
        stats.setTodayChatCount(0L);
        stats.setAiResolveRate(0.0);
        stats.setTransferRate(0.0);
        stats.setTicketCount(0L);
        stats.setAvgResponseTime(0L);
        stats.setSatisfactionRate(0.0);

        return ApiResponse.success("OK", stats);
    }

}
