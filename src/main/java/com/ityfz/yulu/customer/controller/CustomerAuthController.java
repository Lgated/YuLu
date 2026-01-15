package com.ityfz.yulu.customer.controller;


import com.ityfz.yulu.common.enums.ErrorCodes;
import com.ityfz.yulu.common.exception.BizException;
import com.ityfz.yulu.common.model.ApiResponse;
import com.ityfz.yulu.common.security.JwtUtil;
import com.ityfz.yulu.common.tenant.TenantContextHolder;
import com.ityfz.yulu.customer.dto.CustomerLoginRequest;
import com.ityfz.yulu.customer.dto.CustomerRegisterRequest;
import com.ityfz.yulu.common.dto.LoginResponse;
import com.ityfz.yulu.user.entity.Tenant;
import com.ityfz.yulu.user.entity.User;
import com.ityfz.yulu.user.mapper.TenantMapper;
import com.ityfz.yulu.user.mapper.UserMapper;
import com.ityfz.yulu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.Duration;

/**
 * C端认证Controller
 * 权限：无需权限（公开接口）
 */
@RestController
@RequestMapping("/api/customer/auth")
@RequiredArgsConstructor
public class CustomerAuthController {

    private final TenantMapper tenantMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * C端登录
     * POST /api/customer/auth/login
     * 用户需要输入租户标识码
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody CustomerLoginRequest request) {
        // 1. 根据租户标识码查询租户ID（方法内部已校验租户状态）
        Long tenantId = getTenantIdByIdentifier(request.getTenantIdentifier());

        // 2. 设置租户上下文
        TenantContextHolder.setTenantId(tenantId);
        try {
            // 4. 查询用户（必须是USER角色）
            User user = userMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                            .eq(User::getTenantId, tenantId)
                            .eq(User::getUsername, request.getUsername())
                            .eq(User::getRole, "USER")  // C端登录只能是USER角色
            );

            if (user == null || user.getStatus() != 1) {
                throw new BizException(ErrorCodes.USER_NOT_FOUND, "用户不存在或已禁用");
            }

            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                throw new BizException(ErrorCodes.BAD_CREDENTIALS, "用户名或密码错误");
            }

            // 5. 生成Token
            JwtUtil.LoginUser loginUser = new JwtUtil.LoginUser();
            loginUser.setUserId(user.getId());
            loginUser.setTenantId(tenantId);
            loginUser.setRole(user.getRole());
            loginUser.setUsername(user.getUsername());

            String token = JwtUtil.generateToken(loginUser);

            LoginResponse response = new LoginResponse();
            response.setToken(token);
            response.setExpireIn(Duration.ofHours(12).toSeconds());
            response.setUserId(user.getId());
            response.setTenantId(tenantId);
            response.setRole(user.getRole());
            response.setUsername(user.getUsername());

            return ApiResponse.success("登录成功", response);
        } finally {
            TenantContextHolder.clear();
        }
    }

    /**
     * C端注册
     * POST /api/customer/auth/register
     * 用户需要输入租户标识码
     */
    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody CustomerRegisterRequest request) {
        // 1. 根据租户标识码查询租户ID（方法内部已校验租户状态）
        Long tenantId = getTenantIdByIdentifier(request.getTenantIdentifier());

        // 2. 设置租户上下文
        TenantContextHolder.setTenantId(tenantId);
        try {
            // 4. 校验用户名唯一性
            boolean exists = userService.lambdaQuery()
                .eq(User::getTenantId, tenantId)
                .eq(User::getUsername, request.getUsername())
                .exists();
            
            if (exists) {
                throw new BizException(ErrorCodes.USER_EXISTS, "用户名已存在");
            }

            // 5. 创建用户（固定为USER角色）
            User user = new User();
            user.setTenantId(tenantId);
            user.setUsername(request.getUsername());
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setRole("USER");  // C端注册固定为USER角色
            user.setStatus(1);
            user.setNickName(request.getNickName());
            user.setEmail(request.getEmail());
            user.setPhone(request.getPhone());
            userService.save(user);

            // 6. 自动登录（返回Token）
            JwtUtil.LoginUser loginUser = new JwtUtil.LoginUser();
            loginUser.setUserId(user.getId());
            loginUser.setTenantId(tenantId);
            loginUser.setRole(user.getRole());
            loginUser.setUsername(user.getUsername());

            String token = JwtUtil.generateToken(loginUser);

            LoginResponse response = new LoginResponse();
            response.setToken(token);
            response.setExpireIn(Duration.ofHours(12).toSeconds());
            response.setUserId(user.getId());
            response.setTenantId(tenantId);
            response.setRole(user.getRole());
            response.setUsername(user.getUsername());

            return ApiResponse.success("注册成功", response);
        } finally {
            TenantContextHolder.clear();
        }
    }

    /**
     * 根据租户标识码查询租户ID
     */
    private Long getTenantIdByIdentifier(String tenantIdentifier) {
        if (tenantIdentifier == null || tenantIdentifier.isEmpty()) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "租户标识不能为空");
        }

        Tenant tenant = tenantMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Tenant>()
                .eq(Tenant::getTenantIdentifier, tenantIdentifier)
                .eq(Tenant::getStatus, 1)  // 只查询启用的租户
        );

        if (tenant == null) {
            throw new BizException(ErrorCodes.TENANT_NOT_FOUND, 
                "租户标识码不存在: " + tenantIdentifier);
        }

        return tenant.getId();
    }

}
