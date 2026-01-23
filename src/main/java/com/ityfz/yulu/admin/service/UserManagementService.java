package com.ityfz.yulu.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ityfz.yulu.admin.dto.*;

/**
 * 账号管理服务接口
 */
public interface UserManagementService {


    /**
     * 分页查询用户列表
     */
    IPage<UserResponse> listUsers(Long tenantId, UserListRequest request);

    /**
     * 根据ID获取用户详情
     */
    UserResponse getUserById(Long tenantId, Long userId);

    /**
     * 创建用户
     *
     */
    UserResponse createUser(Long tenantId, CreateUserRequest request);

    /**
     * 更新用户信息
     *
     */
    UserResponse updateUser(Long tenantId, Long userId, UpdateUserRequest request,Long currentUserId);

    /**
     * 重置用户密码
     *
     */
    void resetPassword(Long tenantId, Long userId, ResetPasswordRequest request);

    /**
     * 删除用户（软删除：设置status=0）
     *
     * @param tenantId 租户ID
     * @param userId 用户ID
     * @param currentUserId 当前操作人ID（不能删除自己）
     */
    void deleteUser(Long tenantId, Long userId, Long currentUserId);

    /**
     * 启用/禁用用户
     *
     * @param tenantId 租户ID
     * @param userId 用户ID
     * @param status 状态：1-启用，0-禁用
     * @param currentUserId 当前操作人ID（不能禁用自己）
     */
    void updateUserStatus(Long tenantId, Long userId, Integer status, Long currentUserId);
}
