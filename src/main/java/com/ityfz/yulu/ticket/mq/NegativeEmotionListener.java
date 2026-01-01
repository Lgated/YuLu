package com.ityfz.yulu.ticket.mq;

import com.ityfz.yulu.ticket.event.NegativeEmotionEvent;
import com.ityfz.yulu.ticket.service.TicketService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 负向情绪事件监听者（消费者）
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NegativeEmotionListener {
    private final TicketService ticketService;
    private final StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = "q.ticket.emotion.negative")
    public void onNegativeEmotion(
            NegativeEmotionEvent event
            //消息唯一序号（当前 Channel 内递增）
            , @Header(AmqpHeaders.DELIVERY_TAG)long deliveryTag
            //TCP 信道（操作 RabbitMQ 的把手）
            , Channel channel
    ){
        // 幂等性Key：sessionId + question的hash（防止同一会话重复创建工单）
        String questionHash = String.valueOf(event.getQuestion().hashCode());
        String redisKey = String.format("ticket:emotion:%d:%s",
                event.getSessionId(), questionHash);

        try {
            // 1. 幂等性检查
            Boolean exists = stringRedisTemplate.hasKey(redisKey);
            if (Boolean.TRUE.equals(exists)) {
                log.warn("[MQ] 负向情绪消息重复消费，已跳过: sessionId={}", event.getSessionId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 2. 业务处理：创建工单
            ticketService.createTicketOnNegative(
                    event.getTenantId(),
                    event.getUserId(),
                    event.getSessionId(),
                    event.getQuestion(),
                    event.getPriority()
            );

            // 3. 设置Redis标记（1小时过期）
            stringRedisTemplate.opsForValue().set(redisKey, "1", 1, TimeUnit.HOURS);

            // 4. ACK确认
            channel.basicAck(deliveryTag, false);

            log.info("[MQ] 负向情绪工单创建成功: sessionId={}, ticketId=待查询", event.getSessionId());

        } catch (Exception e) {
            log.error("[MQ] 负向情绪工单创建失败: sessionId={}, error={}",
                    event.getSessionId(), e.getMessage(), e);

            try {
                // 5. NACK（进入死信队列）
                channel.basicNack(deliveryTag, false, false);
            } catch (Exception ackException) {
                log.error("[MQ] ACK操作失败", ackException);
            }
        }
    }
}
