package com.ityfz.yulu.handoff.websocket.service;

import com.ityfz.yulu.handoff.entity.HandoffRequest;
import com.ityfz.yulu.handoff.mapper.HandoffRequestMapper;
import com.ityfz.yulu.user.service.AgentStatusService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 智能分配器（多维度评分算法）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAssigner {

    private final HandoffRequestMapper handoffRequestMapper;
    private final AgentStatusService agentStatusService;

    /**
     * 分配客服
     *  @return 分配的客服ID，null表示没有可分配的客服
     */
    public Long assignAgent(Long tenantId,Long handoffRequestId){
        // 查找转人工请求id
        HandoffRequest request = handoffRequestMapper.selectById(handoffRequestId);
        if (request == null) {
            log.warn("[AgentAssigner] 转人工请求不存在：handoffRequestId={}", handoffRequestId);
            return null;
        }

        //1、获取在线客服列表
        List<Long> onlineAgentIds = agentStatusService.getOnlineAgents(tenantId);
        if (onlineAgentIds.isEmpty()) {
            log.info("[AgentAssigner] 没有在线客服：tenantId={}", tenantId);
            return null;
        }
        // 2. 构建候选客服列表
        List<AgentCandidate> candidates = buildCandidates(tenantId, onlineAgentIds);



    }

    private List<AgentCandidate> buildCandidates(Long tenantId, List<Long> onlineAgentIds) {

    }

    /**
     * 候选客服内部类
     */
    @Data
    private static class AgentCandidate {
        private Long agentId;
        private String status;
        private Integer currentSessions;
        private Integer maxSessions;
        private String skillTags;
        private boolean autoAccept;
        private boolean seniorAgent;
        private double score;
    }
}
