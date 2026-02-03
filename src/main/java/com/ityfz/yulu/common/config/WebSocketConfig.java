package com.ityfz.yulu.common.config;

import com.ityfz.yulu.handoff.websocket.AgentWebSocketHandler;
import com.ityfz.yulu.handoff.websocket.CustomerWebSocketHandler;
import com.ityfz.yulu.handoff.websocket.WebSocketHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;


/**
 * WebSocket配置类
 */
@Configuration
@EnableWebSocket

public class WebSocketConfig implements WebSocketConfigurer {

    private final CustomerWebSocketHandler customerWebSocketHandler;
    private final AgentWebSocketHandler agentWebSocketHandler;
    private final WebSocketHandshakeInterceptor handshakeInterceptor;

    public WebSocketConfig(CustomerWebSocketHandler customerWebSocketHandler,
                           AgentWebSocketHandler agentWebSocketHandler,
                           WebSocketHandshakeInterceptor handshakeInterceptor) {
        this.customerWebSocketHandler = customerWebSocketHandler;
        this.agentWebSocketHandler = agentWebSocketHandler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 客户WebSocket连接
        registry.addHandler(customerWebSocketHandler, "/api/ws/customer")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins("*"); // 生产环境应配置具体域名

        // 客服WebSocket连接
        registry.addHandler(agentWebSocketHandler, "/api/ws/agent")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
