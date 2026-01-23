package com.ityfz.yulu.admin.controller;

import com.ityfz.yulu.common.annotation.RequireRole;
import com.ityfz.yulu.common.enums.ErrorCodes;
import com.ityfz.yulu.common.exception.BizException;
import com.ityfz.yulu.common.model.ApiResponse;
import com.ityfz.yulu.common.dto.LoginRequest;
import com.ityfz.yulu.common.dto.LoginResponse;
import com.ityfz.yulu.admin.dto.TenantRegisterRequest;
import com.ityfz.yulu.admin.dto.TenantRegisterResponse;
import com.ityfz.yulu.common.security.SecurityUtil;
import com.ityfz.yulu.user.service.AgentStatusService;
import com.ityfz.yulu.user.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * B端认证Controller
 * API前缀：/api/admin/auth
 * 权限：公开接口（登录和注册无需权限）
 */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
@Tag(name = "B端-认证（Admin/Auth）", description = "租户端登录、租户注册、客服心跳")
public class AdminAuthController {
    private final TenantService tenantService;
    private final AgentStatusService agentStatusService;

    /**
     * B端登录（管理员/客服）
     * POST /api/admin/auth/login
     * 需要tenantCode，因为B端用户可能管理多个租户
     */
    @PostMapping("/login")
    @Operation(summary = "B端登录", description = "管理员/客服登录。成功后返回 JWT Token（Bearer）与 role、tenantId、userId 等")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // B端登录：仅允许 ADMIN/AGENT
        LoginResponse response = tenantService.loginAdmin(request);
        return ApiResponse.success("登录成功", response);
    }

    /**
     * B端租户注册（创建租户+管理员账户）
     * POST /api/admin/auth/registerTenant
     */
    @PostMapping("/registerTenant")
    @Operation(summary = "租户注册", description = "创建租户 + 创建首个管理员账号（ADMIN）")
    public ApiResponse<TenantRegisterResponse> registerTenant(
            @Valid @RequestBody TenantRegisterRequest request) {
        TenantRegisterResponse response = tenantService.registerTenant(request);
        return ApiResponse.success("租户注册成功", response);
    }

    /**
     * 心跳接口（客服保持在线状态）
     * POST /api/admin/user/heartbeat
     */
    @PostMapping("/heartbeat")
    @RequireRole({"ADMIN", "AGENT"})
    @Operation(summary = "客服心跳", description = "用于刷新客服在线状态 TTL（前端建议 30s 一次）")
    public ApiResponse<Void> heartbeat() {
        Long tenantId = SecurityUtil.currentTenantId();
        Long userId = SecurityUtil.currentUserId();

        if (tenantId == null || userId == null) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "请先登录");
        }

        agentStatusService.updateHeartbeat(tenantId, userId);
        return ApiResponse.success("心跳成功");
    }

    //TODO:登出逻辑 ： 加入黑名单、清除在线状态




}
