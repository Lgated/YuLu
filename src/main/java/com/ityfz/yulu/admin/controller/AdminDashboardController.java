package com.ityfz.yulu.admin.controller;

import com.ityfz.yulu.admin.dto.DashboardStats;
import com.ityfz.yulu.admin.service.AdminDashboardService;
import com.ityfz.yulu.admin.vo.DashboardOverviewVO;
import com.ityfz.yulu.common.annotation.RequireRole;
import com.ityfz.yulu.common.enums.ErrorCodes;
import com.ityfz.yulu.common.exception.BizException;
import com.ityfz.yulu.common.model.ApiResponse;
import com.ityfz.yulu.common.security.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * B端数据看板Controller
 * 权限要求：ADMIN 或 AGENT
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@RequireRole({"ADMIN", "AGENT"})
@Tag(name = "B端-数据看板（Admin/Dashboard）", description = "实时 KPI + 趋势数据")
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    //TODO : 优化后端加“定时聚合到Redis”的缓存层，看板接口优先读Redis，兜底查DB并回填缓存 - 已有文档
    @GetMapping("/overview")
    @Operation(summary = "数据看板总览", description = "返回实时KPI和最近趋势")
    public ApiResponse<DashboardOverviewVO> overview( @RequestParam(value = "days", defaultValue = "7") Integer days) {
        Long tenantId = SecurityUtil.currentTenantId();
        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "缺少租户信息，请先登录");
        }
        if (days == null || (days != 7 && days != 30 && days != 90)) {
            throw new BizException(ErrorCodes.PARAM_INVALID, "days 仅支持 7/30/90");
        }
        return ApiResponse.success("OK", dashboardService.getOverview(tenantId,days));
    }
}
