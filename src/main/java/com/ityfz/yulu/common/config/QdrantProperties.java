package com.ityfz.yulu.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Qdrant 向量数据库配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "qdrant")
public class QdrantProperties {
    /**
     * Qdrant 服务地址
     */
    private String host = "localhost";

    /**
     * HTTP 端口（默认 6333）
     */
    private int port = 6333;

    /**
     * gRPC 端口（默认 6334）
     */
    private int grpcPort = 6334;

    /**
     * 连接超时时间（毫秒）
     */
    private int timeout = 5000;

    /**
     * API Key（可选，如果 Qdrant 启用了认证）
     */
    private String apiKey;

}
