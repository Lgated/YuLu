package com.ityfz.yulu.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ityfz.yulu.user.dto.LoginRequest;
import com.ityfz.yulu.user.dto.LoginResponse;
import com.ityfz.yulu.user.dto.TenantRegisterRequest;
import com.ityfz.yulu.user.dto.TenantRegisterResponse;
import com.ityfz.yulu.user.entity.Tenant;

public interface TenantService extends IService<Tenant> {

    /**
     * 租户注册 + 管理员创建。
     */
    TenantRegisterResponse registerTenant(TenantRegisterRequest request);

    /**
     * 登录，返回token等信息。
     */
    LoginResponse login(LoginRequest request);
}

