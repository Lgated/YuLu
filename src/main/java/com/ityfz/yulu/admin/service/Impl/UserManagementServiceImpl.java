package com.ityfz.yulu.admin.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ityfz.yulu.admin.dto.*;
import com.ityfz.yulu.admin.service.UserManagementService;
import com.ityfz.yulu.common.enums.ErrorCodes;
import com.ityfz.yulu.common.enums.Roles;
import com.ityfz.yulu.common.exception.BizException;
import com.ityfz.yulu.user.entity.User;
import com.ityfz.yulu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementServiceImpl implements UserManagementService {

    private final UserService userService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public IPage<UserResponse> listUsers(Long tenantId, UserListRequest request) {
        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "租户ID不能为空");
        }

        // 分页参数
        int page = request.getPage() != null && request.getPage() > 0 ? request.getPage() : 1;
        int size = request.getSize() != null && request.getSize() > 0 && request.getSize() <= 100
                ? request.getSize() : 10;

        Page<User> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        // 角色筛选
        if (StringUtils.hasText(request.getRole())) {
            wrapper.eq(User::getRole, request.getRole().toUpperCase());
        }

        // 状态筛选
        if (request.getStatus() != null) {
            wrapper.eq(User::getStatus, request.getStatus());
        }
        // 关键词搜索（用户名、昵称、邮箱、手机号）
        if (StringUtils.hasText(request.getKeyword())) {
            String keyword = request.getKeyword().trim();
            wrapper.and(w -> w
                    .like(User::getUsername, keyword)
                    .or()
                    .like(User::getNickName, keyword)
                    .or()
                    .like(User::getEmail, keyword)
                    .or()
                    .like(User::getPhone, keyword)
            );
        }

        // 排序：按创建时间倒序
        wrapper.orderByDesc(User::getCreateTime);

        // 查询
        IPage<User> userPage = userService.page(pageParam, wrapper);

        // 转换为响应DTO
        return userPage.convert(this::toUserResponse);
    }

    @Override
    public UserResponse getUserById(Long tenantId, Long userId) {
        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "租户ID不能为空");
        }

        User user = userService.getOne(new LambdaQueryWrapper<User>().eq(User::getTenantId, tenantId)
                .eq(User::getId, userId));

        if (user == null) {
            throw new BizException(ErrorCodes.USER_NOT_FOUND, "用户不存在");
        }

        return toUserResponse(user);
    }

    @Override
    public UserResponse createUser(Long tenantId, CreateUserRequest request) {
        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "租户ID不能为空");
        }

        // 1. 验证角色
        String role = request.getRole().toUpperCase();
        if (!isValidRole(role)) {
            throw new BizException(ErrorCodes.INVALID_ROLE, "角色必须是USER、ADMIN或AGENT");
        }
        // 2. 检查用户名是否已存在（同租户内）
        boolean exists = userService.lambdaQuery()
                .eq(User::getTenantId, tenantId)
                .eq(User::getUsername, request.getUsername())
                .exists();

        if (exists) {
            throw new BizException(ErrorCodes.USER_EXISTS, "用户名已存在");
        }

        // 3. 创建用户
        User user = new User();
        user.setTenantId(tenantId);
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);
        user.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        user.setNickName(request.getNickName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        userService.save(user);
        log.info("[UserManagement] 创建用户成功: tenantId={}, userId={}, username={}, role={}",
                tenantId, user.getId(), user.getUsername(), role);

        return toUserResponse(user);
    }

    @Override
    public UserResponse updateUser(Long tenantId, Long userId, UpdateUserRequest request,Long currentUserId) {
        if (tenantId == null || userId == null) {
            throw new BizException(ErrorCodes.VALIDATION_ERROR, "租户ID和用户ID不能为空");
        }

        // 查找用户
        User user = userService.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, userId)
                .eq(User::getTenantId, tenantId));

        if (user == null) {
            throw new BizException(ErrorCodes.USER_NOT_FOUND, "用户不存在");
        }

        // 2. 更新字段
        boolean updated = false;

        // StringUtils.hasText() ： 非空且非空白判断
        if (StringUtils.hasText(request.getNickName())) {
            user.setNickName(request.getNickName());
            updated = true;
        }

        if (StringUtils.hasText(request.getEmail())) {
            user.setEmail(request.getEmail());
            updated = true;
        }

        if (StringUtils.hasText(request.getPhone())) {
            user.setPhone(request.getPhone());
            updated = true;
        }

        // 3. 更新角色（仅B端用户可修改）
        if (StringUtils.hasText(request.getRole())) {
            String newRole = request.getRole().toUpperCase();

            // 验证：只能修改B端用户的角色（ADMIN/AGENT之间切换）
            if (Roles.isUser(user.getRole())) {
                throw new BizException(ErrorCodes.FORBIDDEN, "C端用户（USER）的角色不能修改");
            }

            if (!Roles.isAdmin(newRole) && !Roles.isAgent(newRole)) {
                throw new BizException(ErrorCodes.INVALID_ROLE, "B端用户角色只能是ADMIN或AGENT");
            }

            if (userId.equals(currentUserId)) {
                throw new BizException(ErrorCodes.FORBIDDEN, "不能禁用自己的账号");
            }
            user.setRole(newRole);
            updated = true;
        }

        // 4. 更新状态
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
            updated = true;
        }

        if (updated) {
            user.setUpdateTime(LocalDateTime.now());
            userService.updateById(user);

            log.info("[UserManagement] 更新用户成功: tenantId={}, userId={}, username={}",
                    tenantId, userId, user.getUsername());
        }

        return toUserResponse(user);
    }

    @Override
    public void resetPassword(Long tenantId, Long userId, ResetPasswordRequest request) {
        if (tenantId == null || userId == null) {
            throw new BizException(ErrorCodes.VALIDATION_ERROR, "租户ID和用户ID不能为空");
        }

        // 1. 查询用户
        User user = userService.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getTenantId, tenantId)
                .eq(User::getId, userId));

        if (user == null) {
            throw new BizException(ErrorCodes.USER_NOT_FOUND, "用户不存在");
        }

        // 2. 重置密码
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdateTime(LocalDateTime.now());
        userService.updateById(user);

        log.info("[UserManagement] 重置密码成功: tenantId={}, userId={}, username={}",
                tenantId, userId, user.getUsername());
    }

    @Override
    public void deleteUser(Long tenantId, Long userId, Long currentUserId) {
        if (tenantId == null || userId == null) {
            throw new BizException(ErrorCodes.VALIDATION_ERROR, "租户ID和用户ID不能为空");
        }

        // 1. 不能删除自己
        if (userId.equals(currentUserId)) {
            throw new BizException(ErrorCodes.FORBIDDEN, "不能删除自己的账号");
        }
        // 2. 查询用户
        User user = userService.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getTenantId, tenantId)
                .eq(User::getId, userId));

        if (user == null) {
            throw new BizException(ErrorCodes.USER_NOT_FOUND, "用户不存在");
        }

        // 3. 软删除：设置status=0
        user.setStatus(0);
        user.setUpdateTime(LocalDateTime.now());
        userService.updateById(user);

        log.info("[UserManagement] 删除用户成功: tenantId={}, userId={}, username={}",
                tenantId, userId, user.getUsername());
    }

    @Override
    public void updateUserStatus(Long tenantId, Long userId, Integer status, Long currentUserId) {
        if (tenantId == null || userId == null || status == null) {
            throw new BizException(ErrorCodes.VALIDATION_ERROR, "参数不能为空");
        }

        if (status != 0 && status != 1) {
            throw new BizException(ErrorCodes.VALIDATION_ERROR, "状态值必须是0或1");
        }

        // 1. 不能禁用自己
        if (userId.equals(currentUserId)) {
            throw new BizException(ErrorCodes.FORBIDDEN, "不能禁用自己的账号");
        }

        // 2. 查询用户
        User user = userService.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getTenantId, tenantId)
                .eq(User::getId, userId));

        if (user == null) {
            throw new BizException(ErrorCodes.USER_NOT_FOUND, "用户不存在");
        }

        // 3. 更新状态
        user.setStatus(status);
        user.setUpdateTime(LocalDateTime.now());
        userService.updateById(user);

        log.info("[UserManagement] 更新用户状态成功: tenantId={}, userId={}, username={}, status={}",
                tenantId, userId, user.getUsername(), status);

    }


    /**
     * 转换为响应DTO
     */
    private UserResponse toUserResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setTenantId(user.getTenantId());
        response.setUsername(user.getUsername());
        response.setRole(user.getRole());
        response.setStatus(user.getStatus());
        response.setNickName(user.getNickName());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());
        response.setCreateTime(user.getCreateTime());
        response.setUpdateTime(user.getUpdateTime());

        // 状态文本
        response.setStatusText(user.getStatus() != null && user.getStatus() == 1 ? "启用" : "禁用");

        // 角色文本
        String role = user.getRole();
        if (Roles.isAdmin(role)) {
            response.setRoleText("管理员");
        } else if (Roles.isAgent(role)) {
            response.setRoleText("客服");
        } else if (Roles.isUser(role)) {
            response.setRoleText("用户");
        } else {
            response.setRoleText(role);
        }

        return response;
    }

    /**
     * 验证角色是否有效
     */
    private boolean isValidRole(String role) {
        return Roles.ADMIN.equalsIgnoreCase(role)
                || Roles.AGENT.equalsIgnoreCase(role)
                || Roles.USER.equalsIgnoreCase(role);
    }
}
