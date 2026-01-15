package com.ityfz.yulu.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ityfz.yulu.admin.dto.UserRegisterRequest;
import com.ityfz.yulu.admin.dto.UserRegisterResponse;
import com.ityfz.yulu.user.entity.User;


public interface UserService extends IService<User> {
    UserRegisterResponse registerUser(UserRegisterRequest request);
}












