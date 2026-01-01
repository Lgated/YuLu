package com.ityfz.yulu.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai.qianwen")
public class QianWenProperties {
    /**
     * 百炼通义千问 API Key
     */
    private String apiKey;

    /**
     * API 根地址，例如：https://dashscope.aliyuncs.com/api/v1
     */
    private String baseUrl;

    /**
     * 模型名称
     */
    private String model;

    // getter / setter
    public String getApiKey() {
        return apiKey;
    }
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }
    public void setModel(String model) {
        this.model = model;
    }
}
