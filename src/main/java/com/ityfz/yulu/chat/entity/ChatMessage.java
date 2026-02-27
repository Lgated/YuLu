package com.ityfz.yulu.chat.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long sessionId;
    private String senderType;// USER / AI / AGENT
    private String content;
    private String emotion;
    private String intent;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
