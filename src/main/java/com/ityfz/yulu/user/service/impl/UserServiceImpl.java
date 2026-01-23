package com.ityfz.yulu.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ityfz.yulu.common.enums.Roles;
import com.ityfz.yulu.common.enums.ErrorCodes;
import com.ityfz.yulu.common.exception.BizException;
import com.ityfz.yulu.admin.dto.UserRegisterRequest;
import com.ityfz.yulu.admin.dto.UserRegisterResponse;
import com.ityfz.yulu.user.entity.Tenant;
import com.ityfz.yulu.user.entity.User;
import com.ityfz.yulu.user.mapper.TenantMapper;
import com.ityfz.yulu.user.mapper.UserMapper;
import com.ityfz.yulu.user.service.UserService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final TenantMapper tenantMapper;
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();


    public UserServiceImpl(TenantMapper tenantMapper,
                           UserMapper userMapper) {
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
    }

    /**
     * 注册用户
     */
    @Override
    public UserRegisterResponse registerUser(UserRegisterRequest request) {
        //1、查租户
        Tenant tenant = tenantMapper.selectOne(
                new LambdaQueryWrapper<Tenant>()
                        .eq(Tenant::getTenantCode, request.getTenantCode()));
        if (tenant == null || tenant.getStatus() != 1) {
            throw new BizException(ErrorCodes.TENANT_NOT_FOUND, "租户不存在或已禁用");
        }

        //2、校验用户名唯一（同租户内）
        boolean exists = this.lambdaQuery()
                .eq(User::getTenantId, tenant.getTenantCode())
                .eq(User::getUsername, request.getUsername())
                .exists();
        if(exists){
            throw new BizException(ErrorCodes.USER_EXISTS, "用户名已存在");
        }

        //3、校验角色
        String role = request.getRole() == null ? "" : request.getRole().toUpperCase();
        if (!isValidRole(role)) {
            throw new BizException(ErrorCodes.INVALID_ROLE, "角色仅支持 ADMIN/AGENT/USER");
        }

        //4、创建角色
        User user = new User();
        user.setTenantId(tenant.getId());
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);
        user.setStatus(1);
        user.setNickName(request.getNickName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        this.save(user);

        // 5、返回
        UserRegisterResponse resp = new UserRegisterResponse();
        resp.setUserId(user.getId());
        resp.setTenantId(tenant.getId());
        resp.setTenantCode(tenant.getTenantCode());
        resp.setUsername(user.getUsername());
        resp.setRole(user.getRole());
        return resp;
    }


    //TODO : 需要查询状态正常且上线的客服
    @Override
    public List<User> listByTenantIdAndRole(Long tenantId, String role) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getTenantId, tenantId)
                .eq(User::getRole, role)
                .eq(User::getStatus, 1) // 只查询正常状态的用户
                .orderByDesc(User::getCreateTime);

        return userMapper.selectList(wrapper);
    }

    private boolean isValidRole(String role) {
        return Roles.ADMIN.equalsIgnoreCase(role)
                || Roles.AGENT.equalsIgnoreCase(role)
                || Roles.USER.equalsIgnoreCase(role);
    }
}


