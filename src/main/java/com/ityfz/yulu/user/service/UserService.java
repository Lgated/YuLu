package com.ityfz.yulu.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ityfz.yulu.admin.dto.UserRegisterRequest;
import com.ityfz.yulu.admin.dto.UserRegisterResponse;
import com.ityfz.yulu.user.entity.User;

import java.util.List;


public interface UserService extends IService<User> {
    UserRegisterResponse registerUser(UserRegisterRequest request);

    /**
     * 根据租户ID和角色查询用户列表
     */
    List<User> listByTenantIdAndRole(Long tenantId, String role);
}












