package com.ityfz.yulu.common.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对话消息，用于封装上下文传给大模型。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    /**
     * 角色：user / assistant
     */
    private String role;

    /**
     * 内容：问题或回答
     */
    private String content;
}
