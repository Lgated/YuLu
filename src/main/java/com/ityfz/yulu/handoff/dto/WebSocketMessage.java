package com.ityfz.yulu.handoff.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WebSocketMessage {

    /**
     * 消息类型
     */
    private String type;

    /**
     * 消息负载
     */
    private Map<String, Object> payload;

    /**
     * 时间戳
     */
    private String timestamp;

    /**
     * 请求ID（可选，用于请求-响应匹配）
     */
    private String requestId;
}
