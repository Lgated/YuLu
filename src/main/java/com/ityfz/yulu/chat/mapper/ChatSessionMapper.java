package com.ityfz.yulu.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ityfz.yulu.chat.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    // 这里可以定义一些自定义的SQL方法，例如查询会话详情、更新会话状态等。
    // 例如：
    ChatSession selectByPrimaryKey(Long id);
}
