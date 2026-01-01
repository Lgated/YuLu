package com.ityfz.yulu.chat.dto;

import lombok.Data;

@Data
public class ChatAskRequest {
    //允许前端指定会话id，不传则自动创建
    private Long sessionId;
    private String question;
}
