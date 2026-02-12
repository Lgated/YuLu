package com.ityfz.yulu.user.service.impl;

import com.ityfz.yulu.user.service.AgentConfigService;
import com.ityfz.yulu.user.service.AgentStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentStatusServiceImpl implements AgentStatusService {

    // 心跳逻辑： 前端设置一个定时器比如说30s，登陆成功后就单向去ping，后端收到就调用更新redis的状态ttl接口
    //          所以，只要定时器在跑，TTL 就被不断重置为 30 min。
    //          一旦客服断网 / 关闭页面 / 电脑休眠 或者，前端就断掉定时器使用，所以就不会更新ttl，就会过期，过期后就设置状态为离线

    private final RedisTemplate<String, Object> redisTemplate;
    private final AgentConfigService agentConfigService;

    //  在线状态存储
    private static final String STATUS_KEY_PREFIX = "agent:status:";
    // 当前会话数存储
    private static final String SESSIONS_KEY_PREFIX = "agent:sessions:";
    // 在线客服列表
    private static final String ONLINE_SET_KEY_PREFIX = "agent:online:";
    // TTL设置
    //默认：30分钟（心跳超时自动清除）
    //登录时：设置TTL为30分钟
    //心跳时：刷新TTL
    private static final int HEARTBEAT_TTL_MINUTES = 30;

    @Override
    public void setOnline(Long tenantId, Long userId) {
        String statusKey = buildStatusKey(tenantId, userId);
        String sessionsKey = buildSessionsKey(tenantId, userId);
        String onlineSetKey = buildOnlineSetKey(tenantId);

        long now = System.currentTimeMillis();
        LocalDateTime nowDateTime = LocalDateTime.now();
        // 设置状态Hash
        Map<String, Object> status = new HashMap<>();
        status.put("status", "ONLINE");
        status.put("last_active_time", nowDateTime.toString());
        status.put("heartbeat_time", now);
        status.put("current_sessions", 0);

        // 获取最大并发数（从配置表）
        Integer maxSessions = agentConfigService.getMaxConcurrentSessions(tenantId, userId);
        status.put("max_sessions", maxSessions != null ? maxSessions : 5);

        redisTemplate.opsForHash().putAll(statusKey, status);
        redisTemplate.expire(statusKey, HEARTBEAT_TTL_MINUTES, TimeUnit.MINUTES);

        // 初始化会话数
        redisTemplate.opsForValue().set(sessionsKey, 0);
        redisTemplate.expire(sessionsKey, HEARTBEAT_TTL_MINUTES, TimeUnit.MINUTES);

        // 添加到在线集合
        redisTemplate.opsForZSet().add(onlineSetKey, userId.toString(), now);
        redisTemplate.expire(onlineSetKey, HEARTBEAT_TTL_MINUTES, TimeUnit.MINUTES);

        log.info("[AgentStatus] 客服上线: tenantId={}, userId={}", tenantId, userId);
    }

    @Override
    public void setOffline(Long tenantId, Long userId) {
        String statusKey = buildStatusKey(tenantId, userId);
        String sessionsKey = buildSessionsKey(tenantId, userId);
        String onlineSetKey = buildOnlineSetKey(tenantId);

        // 删除状态和会话数
        redisTemplate.delete(statusKey);
        redisTemplate.delete(sessionsKey);

        // 从在线集合移除
        redisTemplate.opsForZSet().remove(onlineSetKey, userId.toString());

        log.info("[AgentStatus] 客服下线: tenantId={}, userId={}", tenantId, userId);
    }

    @Override
    public void setAway(Long tenantId, Long userId) {
        String statusKey = buildStatusKey(tenantId, userId);
        redisTemplate.opsForHash().put(statusKey, "status", "AWAY");
        redisTemplate.expire(statusKey, HEARTBEAT_TTL_MINUTES, TimeUnit.MINUTES);

        log.debug("[AgentStatus] 客服离开: tenantId={}, userId={}", tenantId, userId);
    }

    @Override
    public void updateHeartbeat(Long tenantId, Long userId) {
        String statusKey = buildStatusKey(tenantId, userId);
        String onlineSetKey = buildOnlineSetKey(tenantId);

        long now = System.currentTimeMillis();
        LocalDateTime nowDateTime = LocalDateTime.now();
        // 更新心跳时间
        redisTemplate.opsForHash().put(statusKey, "heartbeat_time", now);
        redisTemplate.opsForHash().put(statusKey, "last_active_time", nowDateTime.toString());
        redisTemplate.expire(statusKey, HEARTBEAT_TTL_MINUTES, TimeUnit.MINUTES);

        // 更新在线集合的score
        redisTemplate.opsForZSet().add(onlineSetKey, userId.toString(), now);
        redisTemplate.expire(onlineSetKey, HEARTBEAT_TTL_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public String getStatus(Long tenantId, Long userId) {
        String statusKey = buildStatusKey (tenantId, userId);
        Object status = redisTemplate.opsForHash().get(statusKey, "status");
        return status != null ? status.toString() : "OFFLINE";
    }

    @Override
    public void incrementSessionCount(Long tenantId, Long userId) {
        String sessionsKey = buildSessionsKey(tenantId, userId);
        String statusKey = buildStatusKey(tenantId, userId);

        Long count = redisTemplate.opsForValue().increment(sessionsKey);
        redisTemplate.expire(sessionsKey, HEARTBEAT_TTL_MINUTES, TimeUnit.MINUTES);

        // 同步更新状态Hash
        if (count != null) {
            redisTemplate.opsForHash().put(statusKey, "current_sessions", count);
        }

        log.debug("[AgentStatus] 增加会话数: tenantId={}, userId={}, count={}",
                tenantId, userId, count);
    }

    @Override
    public void decrementSessionCount(Long tenantId, Long userId) {
        String sessionsKey = buildSessionsKey(tenantId, userId);
        String statusKey = buildStatusKey(tenantId, userId);

        Long count = redisTemplate.opsForValue().decrement(sessionsKey);
        if (count != null && count < 0) {
            count = 0L;
            redisTemplate.opsForValue().set(sessionsKey, 0);
        }
        redisTemplate.expire(sessionsKey, HEARTBEAT_TTL_MINUTES, TimeUnit.MINUTES);

        // 同步更新状态Hash
        if (count != null) {
            redisTemplate.opsForHash().put(statusKey, "current_sessions", count);
        }

        log.debug("[AgentStatus] 减少会话数: tenantId={}, userId={}, count={}",
                tenantId, userId, count);
    }

    @Override
    public Integer getCurrentSessionCount(Long tenantId, Long userId) {
        String sessionsKey = buildSessionsKey(tenantId, userId);
        Object count = redisTemplate.opsForValue().get(sessionsKey);
        return count != null ? Integer.parseInt(count.toString()) : 0;
    }

    @Override
    public List<Long> getOnlineAgents(Long tenantId) {
        String onlineSetKey = buildOnlineSetKey(tenantId);
        Set<Object> members = redisTemplate.opsForZSet().range(onlineSetKey, 0, -1);
        List<Long> agentIds = new ArrayList<>();
        if (members != null) {
            for (Object member : members) {
                try {
                    agentIds.add(Long.parseLong(member.toString()));
                } catch (NumberFormatException e) {
                    log.warn("[AgentStatus] 无效的客服ID: {}", member);
                }
            }
        }

        return agentIds;

    }

    @Override
    public Map<String, Object> getAgentStatus(Long tenantId, Long userId) {
        String statusKey = buildStatusKey(tenantId, userId);
        Map<Object, Object> hash = redisTemplate.opsForHash().entries(statusKey);

        Map<String, Object> result = new HashMap<>();
        if (hash != null && !hash.isEmpty()) {
            for (Map.Entry<Object, Object> entry : hash.entrySet()) {
                result.put(entry.getKey().toString(), entry.getValue());
            }
        } else {
            result.put("status", "OFFLINE");
            result.put("current_sessions", 0);
        }

        return result;
    }

    @Override
    public boolean canAcceptSession(Long tenantId, Long userId) {
        Map<String, Object> status = getAgentStatus(tenantId, userId);

        String currentStatus = (String) status.get("status");
        if (!"ONLINE".equals(currentStatus)) {
            return false; // 不在线
        }

        Integer currentSessions = (Integer) status.get("current_sessions");
        Integer maxSessions = (Integer) status.get("max_sessions");

        if (currentSessions == null) currentSessions = 0;
        if (maxSessions == null) maxSessions = 5;

        return currentSessions < maxSessions;
    }

    // 工具方法
    private String buildStatusKey(Long tenantId, Long userId) {
        return STATUS_KEY_PREFIX + tenantId + ":" + userId;
    }

    private String buildSessionsKey(Long tenantId, Long userId) {
        return SESSIONS_KEY_PREFIX + tenantId + ":" + userId;
    }

    private String buildOnlineSetKey(Long tenantId) {
        return ONLINE_SET_KEY_PREFIX + tenantId;
    }
}
