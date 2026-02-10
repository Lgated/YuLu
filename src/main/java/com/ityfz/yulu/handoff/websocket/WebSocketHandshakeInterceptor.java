package com.ityfz.yulu.handoff.websocket;

import com.ityfz.yulu.common.security.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket握手拦截器（用于鉴权）
 */
@Slf4j
@Component
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {


    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            // 从URL参数获取token
            String token = servletRequest.getServletRequest().getParameter("token");
            if (token == null || token.isEmpty()) {
                log.warn("[WebSocket] 握手失败：缺少token");
                return false;
            }

            // 验证token
            try {
                JwtUtil.LoginUser loginUser = JwtUtil.parseToken(token);
                Long userId = loginUser.getUserId();
                Long tenantId = loginUser.getTenantId();
                String role = loginUser.getRole();

                if (userId == null || tenantId == null) {
                    log.warn("[WebSocket] 握手失败：token中缺少必要信息");
                    return false;
                }
                // 将用户信息存储到attributes中，供WebSocketHandler使用
                attributes.put("userId", userId);
                attributes.put("tenantId", tenantId);
                attributes.put("role", role);

                // 如果是客户连接，获取sessionId
                String sessionIdStr = servletRequest.getServletRequest().getParameter("sessionId");
                if (sessionIdStr != null && !sessionIdStr.isEmpty()) {
                    attributes.put("sessionId", Long.parseLong(sessionIdStr));
                }

                log.info("[WebSocket] 握手成功：userId={}, tenantId={}, role={}", userId, tenantId, role);
                return true;
            } catch (Exception e) {
                log.error("[WebSocket] 握手失败：token验证失败", e);
                return false;
            }
        }
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
                // 握手后的处理（如果需要）
    }
}
