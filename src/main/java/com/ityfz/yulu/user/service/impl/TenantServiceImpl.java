package com.ityfz.yulu.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ityfz.yulu.common.enums.Roles;
import com.ityfz.yulu.common.error.ErrorCodes;
import com.ityfz.yulu.common.exception.BizException;
import com.ityfz.yulu.common.security.JwtUtil;
import com.ityfz.yulu.common.security.JwtUtil.LoginUser;
import com.ityfz.yulu.common.tenant.TenantContextHolder;
import com.ityfz.yulu.user.dto.LoginRequest;
import com.ityfz.yulu.user.dto.LoginResponse;
import com.ityfz.yulu.user.dto.TenantRegisterRequest;
import com.ityfz.yulu.user.dto.TenantRegisterResponse;
import com.ityfz.yulu.user.entity.Tenant;
import com.ityfz.yulu.user.entity.User;
import com.ityfz.yulu.user.mapper.TenantMapper;
import com.ityfz.yulu.user.service.TenantService;
import com.ityfz.yulu.user.service.UserService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 租户服务实现。
 */
@Service
public class TenantServiceImpl extends ServiceImpl<TenantMapper, Tenant> implements TenantService {

     private final UserService userService;
     private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

     public TenantServiceImpl(UserService userService) {
         this.userService = userService;
     }

    //注册租户并且注册管理员角色的用户
    @Override
    public TenantRegisterResponse registerTenant(TenantRegisterRequest request) {

        //校验租户编码是否存在
        boolean exists = this.lambdaQuery()
                .eq(Tenant::getTenantCode, request.getTenantCode())
                .exists();
        if (exists) {
            throw new BizException(ErrorCodes.TENANT_EXISTS, "租户编码已存在");
        }

        //租户不存在，新建租户
        Tenant tenant = new Tenant();
        tenant.setTenantCode(request.getTenantCode());
        tenant.setName(request.getTenantName());
        tenant.setStatus(1);
        this.save(tenant);

        //校验用户名是否重复
        boolean adminExists = userService.lambdaQuery()
                .eq(User::getTenantId, tenant.getId())
                .eq(User::getUsername, request.getAdminUsername())
                .exists();
        if (adminExists) {
            throw new BizException(ErrorCodes.USER_EXISTS, "用户名已存在");
        }

        // 确定角色：如果未提供或为空，默认为 ADMIN（保持向后兼容）
        String role = StringUtils.hasText(request.getRole()) 
                ? request.getRole().toUpperCase() 
                : Roles.ADMIN;

        // 验证角色有效性
        if (!isValidRole(role)) {
            throw new BizException(ErrorCodes.INVALID_ROLE, 
                    String.format("无效的角色：%s，仅支持 ADMIN、AGENT、USER", role));
        }

        //创建用户
        User admin = new User();
        admin.setTenantId(tenant.getId());
        admin.setUsername(request.getAdminUsername());
        admin.setPassword(passwordEncoder.encode(request.getAdminPassword()));
        admin.setRole(role);
        admin.setStatus(1);
        userService.save(admin);

        TenantRegisterResponse response = new TenantRegisterResponse();
        response.setTenantId(tenant.getId());
        response.setTenantCode(tenant.getTenantCode());
        response.setAdminUserId(admin.getId());
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

    //登录
    @Override
    public LoginResponse login(LoginRequest request) {
        Tenant tenant = this.lambdaQuery()
                .eq(Tenant::getTenantCode, request.getTenantCode())
                .one();

        if (tenant == null || tenant.getStatus() != 1) {
            throw new BizException(ErrorCodes.TENANT_NOT_FOUND, "租户不存在或已禁用");
        }

        // 设置租户上下文，确保多租户插件能正确工作
        TenantContextHolder.setTenantId(tenant.getId());
        try {
            User user = userService.getOne(new LambdaQueryWrapper<User>()
                    .eq(User::getTenantId, tenant.getId())
                    .eq(User::getUsername, request.getUsername()));
            if (user == null || user.getStatus() != 1) {
                throw new BizException(ErrorCodes.USER_NOT_FOUND, "用户不存在或已禁用");
            }
            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                throw new BizException(ErrorCodes.BAD_CREDENTIALS, "用户名或密码错误");
            }

            // 组装 LoginUser 并生成 token
            LoginUser loginUser = new LoginUser();
            loginUser.setUserId(user.getId());
            loginUser.setTenantId(tenant.getId());
            loginUser.setRole(user.getRole());
            loginUser.setUsername(user.getUsername());

            String token = JwtUtil.generateToken(loginUser);

            LoginResponse response = new LoginResponse();
            response.setToken(token);
            // 与 JwtUtil 中 EXPIRE_HOURS 保持一致
            response.setExpireIn(Duration.ofHours(12).toSeconds());
            response.setUserId(user.getId());
            response.setTenantId(tenant.getId());
            response.setRole(user.getRole());
            response.setUsername(user.getUsername());

            return response;
        } finally {
            // 清除租户上下文（虽然请求结束后会自动清除，但显式清除更安全）
            TenantContextHolder.clear();
        }
    }
}
