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
 * 客服WebSocket Handler
 */
@Slf4j
@Component
public class AgentWebSocketHandler extends TextWebSocketHandler {

    // 存储客服连接：key = "agent:{tenantId}:{agentId}"
    private final Map<String, WebSocketSession> agentSessions = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebSocketMessageService messageService;

    public AgentWebSocketHandler(WebSocketMessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 从attributes获取用户信息
        Long agentId = (Long) session.getAttributes().get("userId");
        Long tenantId = (Long) session.getAttributes().get("tenantId");
        String role = (String) session.getAttributes().get("role");

        if (agentId == null || tenantId == null) {
            log.warn("[WebSocket] 客服连接失败：缺少必要参数");
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        // 验证角色（必须是AGENT或ADMIN）
        if (!"AGENT".equals(role) && !"ADMIN".equals(role)) {
            log.warn("[WebSocket] 客服连接失败：角色不符，role={}", role);
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        // 存储连接
        String connectionKey = buildConnectionKey(tenantId, agentId);
        agentSessions.put(connectionKey, session);

        log.info("[WebSocket] 客服连接建立：tenantId={}, agentId={}, connectionKey={}",
                tenantId, agentId, connectionKey);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            // 解析消息
            WebSocketMessage wsMessage = objectMapper.readValue(message.getPayload(), WebSocketMessage.class);

            // 获取用户信息
            Long agentId = (Long) session.getAttributes().get("userId");
            Long tenantId = (Long) session.getAttributes().get("tenantId");

            // 设置租户上下文（确保 MyBatis-Plus 租户插件正确生效）
            TenantContextHolder.setTenantId(tenantId);
            try {
                // 处理消息
                messageService.handleAgentMessage(tenantId, agentId, wsMessage);
            } finally {
                // 清理租户上下文
                TenantContextHolder.clear();
            }

        } catch (Exception e) {
            log.error("[WebSocket] 处理客服消息失败", e);
            // 发送错误消息给客服
            sendErrorMessage(session, "消息处理失败：" + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // 清理连接
        Long agentId = (Long) session.getAttributes().get("userId");
        Long tenantId = (Long) session.getAttributes().get("tenantId");

        if (agentId != null && tenantId != null) {
            String connectionKey = buildConnectionKey(tenantId, agentId);
            agentSessions.remove(connectionKey);
            log.info("[WebSocket] 客服连接关闭：connectionKey={}, status={}", connectionKey, status);
        }
    }

    /**
     * 发送消息给客服
     */
    // TODO: 添加同步锁，避免并发发送消息导致的错误
    public void sendToAgent(Long tenantId, Long agentId, WebSocketMessage message) {
        String connectionKey = buildConnectionKey(tenantId, agentId);
        WebSocketSession session = agentSessions.get(connectionKey);

        if (session != null && session.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(json));
                log.debug("[WebSocket] 发送消息给客服：connectionKey={}, type={}", connectionKey, message.getType());
            } catch (Exception e) {
                log.error("[WebSocket] 发送消息给客服失败：connectionKey={}", connectionKey, e);
            }
        } else {
            log.warn("[WebSocket] 客服连接不存在或已关闭：connectionKey={}", connectionKey);
        }
    }

    /**
     * 构建连接key
     */
    private String buildConnectionKey(Long tenantId, Long agentId) {
        return "agent:" + tenantId + ":" + agentId;
    }

    /**
     * 发送错误消息
     */
    private void sendErrorMessage(WebSocketSession session, String errorMsg) {
        try {
            WebSocketMessage errorMessage = WebSocketMessage.builder()
                    .type("ERROR")
                    .payload(Map.of("message", errorMsg))
                    .timestamp(java.time.LocalDateTime.now().toString())
                    .build();
            String json = objectMapper.writeValueAsString(errorMessage);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("[WebSocket] 发送错误消息失败", e);
        }
    }

}
