package com.ityfz.yulu.user.service;

import java.util.List;
import java.util.Map;

/**
 * 客服状态服务（基于Redis）
 */
public interface AgentStatusService {

    /**
     * 设置在线状态
     */
    void setOnline(Long tenantId, Long userId);

    /**
     * 设置离线状态
     */
    void setOffline(Long tenantId, Long userId);

    /**
     * 设置离开状态
     */
    void setAway(Long tenantId, Long userId);

    /**
     * 更新心跳时间
     */
    void updateHeartbeat(Long tenantId, Long userId);

    /**
     * 获取在线状态
     */
    String getStatus(Long tenantId, Long userId);

    /**
     * 增加当前会话数
     */
    void incrementSessionCount(Long tenantId, Long userId);

    /**
     * 减少当前会话数
     */
    void decrementSessionCount(Long tenantId, Long userId);

    /**
     * 获取当前会话数
     */
    Integer getCurrentSessionCount(Long tenantId, Long userId);

    /**
     * 获取在线客服列表
     */
    List<Long> getOnlineAgents(Long tenantId);

    /**
     * 获取客服完整状态信息
     */
    Map<String, Object> getAgentStatus(Long tenantId, Long userId);

    /**
     * 检查是否可以接入新会话（未达到最大并发数）
     */
    boolean canAcceptSession(Long tenantId, Long userId);

}
