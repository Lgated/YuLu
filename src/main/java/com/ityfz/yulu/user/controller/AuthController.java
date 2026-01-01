package com.ityfz.yulu.user.controller;

import com.ityfz.yulu.common.model.ApiResponse;
import com.ityfz.yulu.user.dto.*;
import com.ityfz.yulu.user.service.TenantService;
import javax.validation.Valid;

import com.ityfz.yulu.user.service.UserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final TenantService tenantService;
    private final UserService userService;

    public AuthController(TenantService tenantService,UserService userService) {
        this.tenantService = tenantService;
        this.userService = userService;
    }

    /**
     * 租户注册 + 管理员账户注册
     */
    @PostMapping("/registerTenant")
    public ApiResponse<TenantRegisterResponse> registerTenant(@Valid @RequestBody TenantRegisterRequest request) {
        TenantRegisterResponse response = tenantService.registerTenant(request);
        return ApiResponse.success("租户注册成功", response);
    }


    /**
     * 用户注册
     */
    @PostMapping("/registerUser")
    public ApiResponse<UserRegisterResponse> registerUser(@Valid @RequestBody UserRegisterRequest request) {
        UserRegisterResponse response = userService.registerUser(request);
        return ApiResponse.success("用户注册成功", response);
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = tenantService.login(request);
        return ApiResponse.success("登录成功", response);
    }
}
