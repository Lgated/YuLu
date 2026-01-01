package com.ityfz.yulu.ticket.mq;

import com.ityfz.yulu.ticket.entity.Ticket;
import com.ityfz.yulu.ticket.event.TicketAssignedEvent;
import com.ityfz.yulu.ticket.service.NotificationService;
import com.rabbitmq.client.AMQP;
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
 * 工单分配事件监听者（消费者）
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TicketAssignedListener {

    private final NotificationService notificationService;
    private final StringRedisTemplate stringRedisTemplate;


    /**
     * 消费工单分配事件
     * @param event 事件对象
     * @param deliveryTag 消息标签（用于ACK）
     * @param channel （用于ACK/NACK）
     */
    //事件本身 + 快递单号 + 快递柜把手
    //
    @RabbitListener(queues = "q.ticket.assigned")
    public void onTicketAssigned(
            TicketAssignedEvent event,
            //消息唯一序号（当前 Channel 内递增）-- 快递单号
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
            //TCP 信道（操作 RabbitMQ 的把手）-- 快递柜把手
            Channel channel) {
        //生成redis的key，用于后续的幂等性检查
        String redisKey = String.format("notify:ticket:%d:%d",
                event.getTicketId(), event.getAssigneeUserId());


        try {
            // 1. 幂等性检查（Redis去重）
            Boolean exists = stringRedisTemplate.hasKey(redisKey);
            if (Boolean.TRUE.equals(exists)) {
                log.warn("[MQ] 消息重复消费，已跳过: ticketId={}, assigneeUserId={}",
                        event.getTicketId(), event.getAssigneeUserId());
                // 手动ACK，确认消息已处理（即使是重复的）
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 2. 业务处理：写入通知表
            notificationService.notifyAssignment(
                    event.getTenantId(),
                    event.getAssigneeUserId(),
                    event.getTicketId(),
                    event.getTitle(),
                    event.getPriority()
            );

            // 3. 设置Redis标记（24小时过期，防止重复消费）
            stringRedisTemplate.opsForValue().set(redisKey, "1", 24, TimeUnit.HOURS);

            // 4. 手动ACK确认消费成功
            //告诉 RabbitMQ：这条消息我完全处理成功，你可以从队列删除了。
            channel.basicAck(deliveryTag, false);
            log.info("[MQ] 工单分配通知处理成功: ticketId={}, assigneeUserId={}",
                    event.getTicketId(), event.getAssigneeUserId());
        } catch (Exception e) {
            log.error("[MQ] 工单分配通知处理失败: ticketId={}, error={}",
                    event.getTicketId(), e.getMessage(), e);
            try {
                // 5. 失败处理：NACK + requeue=false（不重新入队，进入死信队列）
                channel.basicNack(deliveryTag, false, false);
            } catch (Exception ackException) {
                log.error("[MQ] ACK操作失败", ackException);
            }
        }
    }
}
