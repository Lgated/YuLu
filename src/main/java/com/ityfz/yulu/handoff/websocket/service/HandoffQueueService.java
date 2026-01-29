package com.ityfz.yulu.handoff.websocket.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 转人工排队队列服务（基于Redis）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HandoffQueueService {


    private final RedisTemplate<String, Object> redisTemplate;

    private static final String QUEUE_KEY_PREFIX = "handoff:queue:";
    private static final int QUEUE_TTL_HOURS = 1; // 队列1小时过期

    /**
     * 加入排队队列
     * @return 排队位置（从1开始）
     */
    public int addToQueue(Long tenantId, Long handoffRequestId) {
        String queueKey = QUEUE_KEY_PREFIX + tenantId;
        // 请求塞队尾，返回位置
        Long position = redisTemplate.opsForList().rightPush(queueKey, handoffRequestId.toString());
        //每次插入都重新给 key 设置过期时间
        redisTemplate.expire(queueKey, QUEUE_TTL_HOURS, TimeUnit.HOURS);
        int queuePosition = position != null ? position.intValue() : 0;
        log.info("[HandoffQueue] 加入队列：tenantId={}, handoffRequestId={}, position={}",
                tenantId, handoffRequestId, queuePosition);
        return queuePosition;
    }

    /**
     * 获取排队位置
     * @return 排队位置（从1开始 0表示不在队列中
     */
    public int getQueuePosition(Long tenantId,Long handoffRequestId) {
        String queueKey = QUEUE_KEY_PREFIX + tenantId;
        List<Object> queue = redisTemplate.opsForList().range(queueKey, 0, -1);
        if (queue == null || queue.isEmpty()) {
            return 0;
        }

        String requestIdStr = handoffRequestId.toString();
        for (int i = 0; i < queue.size(); i++) {
            if (requestIdStr.equals(queue.get(i).toString())) {
                return i + 1; //位置从1开始
            }
        }
        return 0;
    }

    /**
     * 从队列移除
     */
    public void removeFromQueue(Long tenantId, Long handoffRequestId) {
        String queueKey = QUEUE_KEY_PREFIX + tenantId;
        Long removed = redisTemplate.opsForList().remove(queueKey, 1, handoffRequestId.toString());

        if (removed != null && removed > 0) {
            log.info("[HandoffQueue] 从队列移除：tenantId={}, handoffRequestId={}", tenantId, handoffRequestId);
        }
    }

    /**
     * 获取队列长度
     */
    public int getQueueLength(Long tenantId) {
        String queueKey = QUEUE_KEY_PREFIX + tenantId;
        Long length = redisTemplate.opsForList().size(queueKey);
        return length != null ? length.intValue() : 0;
    }

    /**
     * 获取队列头部元素（不移除）
     */
    public Long peekQueue(Long tenantId) {
        String queueKey = QUEUE_KEY_PREFIX + tenantId;
        Object first = redisTemplate.opsForList().index(queueKey, 0);

        if (first != null) {
            try {
                return Long.parseLong(first.toString());
            } catch (NumberFormatException e) {
                log.warn("[HandoffQueue] 队列元素格式错误：{}", first);
            }
        }
        return null;
    }

    /**
     * 从队列头部取出元素（移除）
     */
    public Long pollQueue(Long tenantId) {
        String queueKey = QUEUE_KEY_PREFIX + tenantId;
        // 弹出队首
        Object first = redisTemplate.opsForList().leftPop(queueKey);

        if (first != null) {
            try {
                Long handoffRequestId = Long.parseLong(first.toString());
                log.info("[HandoffQueue] 从队列取出：tenantId={}, handoffRequestId={}", tenantId, handoffRequestId);
                return handoffRequestId;
            } catch (NumberFormatException e) {
                log.warn("[HandoffQueue] 队列元素格式错误：{}", first);
            }
        }
        return null;
    }
}

