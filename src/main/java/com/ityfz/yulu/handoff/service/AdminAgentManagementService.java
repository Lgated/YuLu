package com.ityfz.yulu.handoff.service;

import com.ityfz.yulu.handoff.vo.AgentMonitorVO;

import java.util.List;

public interface AdminAgentManagementService {


    /**
     * 获取租户内所有客服的实时监控数据
     */
    public List<AgentMonitorVO> getAgentMonitorList(Long tenantId);

    /**
     * 强制修改客服状态并同步通知
     */
    public void forceUpdateAgentStatus(Long tenantId, Long agentId, String newStatus);

    /**
     * 广播通知
     */
    public void broadcastNotification(Long tenantId, Long senderUserId, String title, String content);
}
