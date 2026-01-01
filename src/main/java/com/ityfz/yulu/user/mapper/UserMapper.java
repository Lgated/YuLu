package com.ityfz.yulu.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ityfz.yulu.user.dto.UserRegisterRequest;
import com.ityfz.yulu.user.dto.UserRegisterResponse;
import com.ityfz.yulu.user.entity.User;
import org.apache.ibatis.annotations.Mapper;


@Mapper
public interface UserMapper extends BaseMapper<User> {

}












