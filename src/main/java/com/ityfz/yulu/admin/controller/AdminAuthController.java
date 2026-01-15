package com.ityfz.yulu.admin.controller;

import com.ityfz.yulu.common.model.ApiResponse;
import com.ityfz.yulu.common.dto.LoginRequest;
import com.ityfz.yulu.common.dto.LoginResponse;
import com.ityfz.yulu.admin.dto.TenantRegisterRequest;
import com.ityfz.yulu.admin.dto.TenantRegisterResponse;
import com.ityfz.yulu.user.service.TenantService;
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
public class AdminAuthController {
    private final TenantService tenantService;

    /**
     * B端登录（管理员/客服）
     * POST /api/admin/auth/login
     * 需要tenantCode，因为B端用户可能管理多个租户
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // B端登录：仅允许 ADMIN/AGENT
        LoginResponse response = tenantService.loginAdmin(request);
        return ApiResponse.success("登录成功", response);
    }

    /**
     * B端租户注册（创建租户+管理员账户）
     * POST /api/admin/auth/registerTenant
     * 公开接口，无需权限
     */
    @PostMapping("/registerTenant")
    public ApiResponse<TenantRegisterResponse> registerTenant(
            @Valid @RequestBody TenantRegisterRequest request) {
        TenantRegisterResponse response = tenantService.registerTenant(request);
        return ApiResponse.success("租户注册成功", response);
    }
}
