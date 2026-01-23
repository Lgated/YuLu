package com.ityfz.yulu.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ityfz.yulu.admin.dto.*;
import com.ityfz.yulu.admin.service.UserManagementService;
import com.ityfz.yulu.common.annotation.RequireRole;
import com.ityfz.yulu.common.enums.ErrorCodes;
import com.ityfz.yulu.common.exception.BizException;
import com.ityfz.yulu.common.model.ApiResponse;
import com.ityfz.yulu.common.security.SecurityUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/admin/user-management")
@RequireRole("ADMIN")
@Validated
public class AdminUserManagementController {

    private final UserManagementService userManagementService;

    public AdminUserManagementController(UserManagementService userManagementService){
        this.userManagementService = userManagementService;
    }

    /**
     * 分页查询用户列表
     */
    @GetMapping("/list")
    public ApiResponse<IPage<UserResponse>> listUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ){
        Long tenantId = SecurityUtil.currentTenantId();
        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "缺少租户信息，请先登录");
        }

        UserListRequest request = new UserListRequest();
        request.setRole(role);
        request.setStatus(status);
        request.setKeyword(keyword);
        request.setPage(page);
        request.setSize(size);
        IPage<UserResponse> result = userManagementService.listUsers(tenantId, request);
        return ApiResponse.success("查询成功", result);
    }

    /**
     * 获取用户详情
     */
    @GetMapping("/{userId}")
    public ApiResponse<UserResponse> getUserById(@PathVariable Long userId) {
        Long tenantId = SecurityUtil.currentTenantId();
        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "缺少租户信息，请先登录");
        }

        UserResponse user = userManagementService.getUserById(tenantId, userId);
        return ApiResponse.success("查询成功", user);
    }

    /**
     * 创建用户
     */
    @PostMapping("/create")
    public ApiResponse<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        Long tenantId = SecurityUtil.currentTenantId();
        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "缺少租户信息，请先登录");
        }

        UserResponse user = userManagementService.createUser(tenantId, request);
        return ApiResponse.success("创建用户成功", user);
    }
    /**
     * 更新用户信息
     */
    @PutMapping("/{userId}")
    public ApiResponse<UserResponse> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        Long tenantId = SecurityUtil.currentTenantId();
        Long currentUserId = SecurityUtil.currentUserId();
        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "缺少租户信息，请先登录");
        }

        UserResponse user = userManagementService.updateUser(tenantId, userId, request,currentUserId);
        return ApiResponse.success("更新用户成功", user);
    }

    /**
     * 重置用户密码
     */
    @PostMapping("/{userId}/reset-password")
    public ApiResponse<Void> resetPassword(
            @PathVariable Long userId,
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        Long tenantId = SecurityUtil.currentTenantId();
        Long currentUserId = SecurityUtil.currentUserId();

        if (tenantId == null || currentUserId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "缺少租户信息，请先登录");
        }

        userManagementService.resetPassword(tenantId, userId, request);
        return ApiResponse.success("重置密码成功");
    }


    /**
     * 删除用户（软删除）
     */
    @DeleteMapping("/{userId}")
    public ApiResponse<Void> deleteUser(@PathVariable Long userId) {
        Long tenantId = SecurityUtil.currentTenantId();
        Long currentUserId = SecurityUtil.currentUserId();

        if (tenantId == null || currentUserId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "缺少租户信息，请先登录");
        }

        userManagementService.deleteUser(tenantId, userId, currentUserId);
        return ApiResponse.success("删除用户成功");
    }



    /**
     * 启用/禁用用户
     */
    @PutMapping("/{userId}/status")
    public ApiResponse<Void> updateUserStatus(
            @PathVariable Long userId,
            @RequestParam Integer status
    ) {
        Long tenantId = SecurityUtil.currentTenantId();
        Long currentUserId = SecurityUtil.currentUserId();

        if (tenantId == null || currentUserId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "缺少租户信息，请先登录");
        }

        userManagementService.updateUserStatus(tenantId, userId, status, currentUserId);
        return ApiResponse.success(status == 1 ? "启用用户成功" : "禁用用户成功");
    }
}
