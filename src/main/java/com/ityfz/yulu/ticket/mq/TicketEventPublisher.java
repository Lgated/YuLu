package com.ityfz.yulu.ticket.mq;

import com.ityfz.yulu.ticket.event.NegativeEmotionEvent;
import com.ityfz.yulu.ticket.event.TicketAssignedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 工单事件发布者（生产者）
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TicketEventPublisher {
    private final RabbitTemplate rabbitTemplate;

    /**
     * 发布工单分配事件
     */
    public void publishAssigned(TicketAssignedEvent event) {
        try {
            rabbitTemplate.convertAndSend("ex.ticket", "ticket.assigned", event,message -> {
                //把消息设为“持久化”（落地磁盘）
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return message;
            });
            log.info("[MQ] 工单分配事件已发布: ticketId={}, assigneeUserId={}",
                    event.getTicketId(), event.getAssigneeUserId());
        } catch (Exception e) {
            log.error("[MQ] 工单分配事件发布失败: ticketId={}, error={}",
                    event.getTicketId(), e.getMessage(), e);
            // 这里可以选择：1. 抛异常回滚事务 2. 记录到本地日志表 3. 降级到同步通知
            throw new RuntimeException("消息发送失败", e);
        }
    }

    /**
     * 发布负向情绪检测事件
     */
    public void publishNegativeEmotion(NegativeEmotionEvent event) {
        try{
            rabbitTemplate.convertAndSend("ex.ticket","ticket.emotion.negative", event, message -> {
                // 设置消息持久化（落地磁盘，防止服务器重启丢失）
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                // 设置消息过期时间（24小时，防止消息积压）
                message.getMessageProperties().setExpiration("86400000"); // 24小时 = 86400000毫秒
                return message;
            });
            log.info("[MQ] 负向情绪事件已发布: sessionId={}, emotion={}",
                    event.getSessionId(), event.getEmotion());
        } catch (Exception e) {
            log.error("[MQ] 负向情绪事件发布失败: sessionId={}, error={}",
                    event.getSessionId(), e.getMessage(), e);
            throw new RuntimeException("消息发送失败", e);
        }
    }
}
