package com.ityfz.yulu.handoff.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ityfz.yulu.handoff.dto.WebSocketMessage;
import com.ityfz.yulu.handoff.websocket.service.WebSocketMessageService;
import com.ityfz.yulu.common.tenant.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户WebSocket Handler
 */
@Slf4j
@Component
public class CustomerWebSocketHandler extends TextWebSocketHandler {

    // TODO:为什么要在这里拿一个map存储？不用redi来存？
    // 存储客户连接：key = "customer:{tenantId}:{userId}:{sessionId}"
    private final Map<String, WebSocketSession> customerSessions = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebSocketMessageService messageService;

    public CustomerWebSocketHandler(WebSocketMessageService messageService) {
        this.messageService = messageService;
    }


    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 从attributes获取用户信息
        Long userId = (Long) session.getAttributes().get("userId");
        Long tenantId = (Long) session.getAttributes().get("tenantId");
        Long sessionId = (Long) session.getAttributes().get("sessionId");

        if (userId == null || tenantId == null || sessionId == null) {
            log.warn("[WebSocket] 客户连接失败：缺少必要参数");
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        // 存储连接
        String connectionKey = buildConnectionKey(tenantId, userId, sessionId);
        customerSessions.put(connectionKey, session);

        log.info("[WebSocket] 客户连接建立：tenantId={}, userId={}, sessionId={}, connectionKey={}",
                tenantId, userId, sessionId, connectionKey);

    }


    // 处理消息
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            // 解析消息
            WebSocketMessage wsMessage = objectMapper.readValue(message.getPayload(), WebSocketMessage.class);

            // 获取用户信息
            Long userId = (Long) session.getAttributes().get("userId");
            Long tenantId = (Long) session.getAttributes().get("tenantId");
            Long sessionId = (Long) session.getAttributes().get("sessionId");

            // 设置租户上下文
            TenantContextHolder.setTenantId(tenantId);
            try {
                // 处理消息
                messageService.handleCustomerMessage(tenantId, userId, sessionId, wsMessage);
            } finally {
                // 清理租户上下文
                TenantContextHolder.clear();
            }

        } catch (Exception e) {
            log.error("[WebSocket] 处理客户消息失败", e);
            // 发送错误消息给客户
            sendErrorMessage(session, "消息处理失败：" + e.getMessage());
        }
    }


    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // 清理连接
        Long userId = (Long) session.getAttributes().get("userId");
        Long tenantId = (Long) session.getAttributes().get("tenantId");
        Long sessionId = (Long) session.getAttributes().get("sessionId");

        if (userId != null && tenantId != null && sessionId != null) {
            String connectionKey = buildConnectionKey(tenantId, userId, sessionId);
            customerSessions.remove(connectionKey);
            log.info("[WebSocket] 客户连接关闭：connectionKey={}, status={}", connectionKey, status);
        }
    }


    /**
     * 发送消息给客户
     */
    public void sendToCustomer(Long tenantId, Long userId, Long sessionId, WebSocketMessage message) {
        String connectionKey = buildConnectionKey(tenantId, userId, sessionId);
        WebSocketSession session = customerSessions.get(connectionKey);

        if (session != null && session.isOpen()) {
            try {
                // 把任意 Java 对象（message）序列化成 JSON 字符串。
                String json = objectMapper.writeValueAsString(message);
                synchronized (session) {
                    session.sendMessage(new TextMessage(json));
                }
                log.debug("[WebSocket] 发送消息给客户：connectionKey={}, type={}", connectionKey, message.getType());
            } catch (Exception e) {
                log.error("[WebSocket] 发送消息给客户失败：connectionKey={}", connectionKey, e);
            }
        } else {
            log.warn("[WebSocket] 客户连接不存在或已关闭：connectionKey={}", connectionKey);
        }
    }


    /**
     * 设置创建连接的键
     */
    private String buildConnectionKey(Long tenantId, Long userId, Long sessionId) {
        return "customer:" + tenantId + ":" + userId + ":" + sessionId;
    }

    /**
     * 发送错误信息
     */
    private void sendErrorMessage(WebSocketSession session, String errorMsg) {
        try {
            WebSocketMessage errorMessage = WebSocketMessage.builder()
                    .type("ERROR")
                    .payload(Map.of("message", errorMsg))
                    .timestamp(java.time.LocalDateTime.now().toString())
                    .build();
            String json = objectMapper.writeValueAsString(errorMessage);
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            log.error("[WebSocket] 发送错误消息失败", e);
        }
    }

}
