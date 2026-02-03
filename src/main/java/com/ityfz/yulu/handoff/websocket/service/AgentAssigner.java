package com.ityfz.yulu.handoff.websocket.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ityfz.yulu.handoff.entity.HandoffRequest;
import com.ityfz.yulu.handoff.mapper.HandoffRequestMapper;
import com.ityfz.yulu.user.entity.AgentConfig;
import com.ityfz.yulu.user.entity.User;
import com.ityfz.yulu.user.mapper.AgentConfigMapper;
import com.ityfz.yulu.user.mapper.UserMapper;
import com.ityfz.yulu.user.service.AgentStatusService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.weaver.loadtime.Agent;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 智能分配器（多维度评分算法）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAssigner {

    private final HandoffRequestMapper handoffRequestMapper;
    private final AgentStatusService agentStatusService;
    private final UserMapper userMapper;
    private final AgentConfigMapper agentConfigMapper;


    /**
     * 分配客服
     *
     * @return 分配的客服ID，null表示没有可分配的客服
     */
    public Long assignAgent(Long tenantId, Long handoffRequestId) {

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
        // 3. 多维度评分
        for (AgentCandidate candidate : candidates) {
            double score = calculateScore(candidate, request);
            candidate.setScore(score);
        }

        // 4.排序并选择最优客服 - 降序
        candidates.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

        // 5. 选择第一个可接入的客服
        for (AgentCandidate candidate : candidates) {
            if (agentStatusService.canAcceptSession(tenantId, candidate.getAgentId())) {
                log.info("[AgentAssigner] 分配客服成功：handoffRequestId={}, agentId={}, score={}",
                        handoffRequestId, candidate.getAgentId(), candidate.getScore());
                return candidate.getAgentId();
            }
        }

        log.info("[AgentAssigner] 没有可接入的客服（负载已满）：tenantId={}", tenantId);
        return null;
    }

    // 计算评分
    private double calculateScore(AgentCandidate candidate, HandoffRequest request) {

        double score = 0.0;
        // 1. 在线状态权重：40%
        if ("ONLINE".equals(candidate.getStatus())) {
            score += 40;
        } else if ("AWAY".equals(candidate.getStatus())) {
            score += 20;
        }

        // 2.负载权重：30%
        if (candidate.getMaxSessions() > 0) {
            double loadRadio = candidate.getCurrentSessions() / (double) candidate.getMaxSessions();
            score += 30 * (1 - loadRadio);
        }

        // 3.技术匹配权重：20%（如果有技能标签匹配，加分）
        // TODO : 这里简化处理，实际可以根据转人工原因匹配技能标签
        if (candidate.getSkillTags() != null && !candidate.getSkillTags().isEmpty()) {
            score += 20; // 有技能标签就加分
        }

        // 4. 历史服务质量权重：10%（这里简化，TODO： 实际可以从历史数据计算）
        score += 10; // 默认给10分

        // 5. 优先级加成（URGENT优先分配给高级客服）
        if ("URGENT".equals(request.getPriority()) && candidate.isSeniorAgent()) {
            score += 10;
        }

        // 6. 自动接入加成（如果配置了自动接入，优先分配）TODO: 自动配置
        if (candidate.isAutoAccept()) {
            score += 5;
        }

        return score;
    }

    /**
     * 构建候选客服列表
     */
    private List<AgentCandidate> buildCandidates(Long tenantId, List<Long> agentIds) {

        List<AgentCandidate> candidates = new ArrayList<>();
        for (Long agentId : agentIds) {
            // 获取用户信息
            User user = userMapper.selectById(agentId);
            if (user == null || !user.getTenantId().equals(tenantId)) {
                continue;
            }

            // 获取客服配置
            AgentConfig config = agentConfigMapper.selectOne(Wrappers.<AgentConfig>lambdaQuery()
                    .eq(AgentConfig::getTenantId, tenantId)
                    .eq(AgentConfig::getUserId, agentId)
                    .last("LIMIT 1"));

            // 获取在线状态
            Map<String, Object> status = agentStatusService.getAgentStatus(tenantId, agentId);
            String currentStatus = (String) status.getOrDefault("status", "OFFLINE");
            Integer currentSessions = (Integer) status.getOrDefault("current_sessions", 0);
            Integer maxSessions = (Integer) status.getOrDefault("max_sessions", 5);

            // 构建候选对象
            AgentCandidate candidate = new AgentCandidate();
            candidate.setAgentId(agentId);
            candidate.setStatus(currentStatus);
            candidate.setCurrentSessions(currentSessions);
            candidate.setMaxSessions(maxSessions);
            candidate.setSkillTags(config != null && config.getSkillTags() != null
                    ? config.getSkillTags() : "");
            candidate.setAutoAccept(config != null && config.getAutoAccept() != null
                    && config.getAutoAccept() == 1);
            candidate.setSeniorAgent("ADMIN".equals(user.getRole())); // 管理员视为高级客服

            candidates.add(candidate);
        }
        return candidates;
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
