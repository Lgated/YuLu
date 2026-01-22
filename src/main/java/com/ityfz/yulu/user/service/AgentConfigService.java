package com.ityfz.yulu.user.service;

import com.ityfz.yulu.user.entity.AgentConfig;

public interface AgentConfigService {
    /**
     * 获取客服配置（如果不存在则创建默认配置）
     */
    AgentConfig getOrCreateConfig(Long tenantId, Long userId);

    /**
     * 更新客服配置
     */
    void updateConfig(Long tenantId, Long userId, AgentConfig config);

    /**
     * 获取最大并发会话数
     */
    Integer getMaxConcurrentSessions(Long tenantId, Long userId);
}
