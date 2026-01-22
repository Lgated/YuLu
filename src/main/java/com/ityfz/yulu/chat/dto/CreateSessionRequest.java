package com.ityfz.yulu.chat.dto;

import lombok.Data;

/**
 * 创建会话请求
 */
@Data
public class CreateSessionRequest {

    /**
     * 会话标题（可选，如果不提供则自动生成）
     */
    private String title;

}
