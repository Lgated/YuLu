package com.ityfz.yulu.ticket.mq;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

/**
 * RabbitMQ配置类
 * 定义交换机、队列、绑定关系
 */
@Configuration
@EnableRabbit
public class TicketMqConfig {
    // ==================== Exchange（交换机） ====================

    /**
     * 工单主题交换机（Topic Exchange）
     */
    @Bean
    //模糊匹配，一个交换机可以有多个队列绑定
    public TopicExchange ticketExchange() {
        return ExchangeBuilder
                .topicExchange("ex.ticket")
                .durable(true)  // 持久化
                .build();
    }

    // ==================== Queue（队列） ====================

    /**
     * 工单分配通知队列
     */
    @Bean
    public Queue ticketAssignedQueue() {
        return QueueBuilder
                .durable("q.ticket.assigned")
                // 配置死信队列
                .withArgument("x-dead-letter-exchange", "ex.ticket")
                .withArgument("x-dead-letter-routing-key", "ticket.dlq")
                // 设置消息过期时间（7天）
                .withArgument("x-message-ttl", 604800000)
                .build();
    }

    /**
     * 负向情绪检测队列
     */
    @Bean
    public Queue negativeEmotionQueue() {
        return QueueBuilder
                .durable("q.ticket.emotion.negative")
                // 配置死信队列：消息处理失败后转发到死信队列
                .withArgument("x-dead-letter-exchange", "ex.ticket")
                .withArgument("x-dead-letter-routing-key", "ticket.dlq")
                // 设置消息过期时间（24小时）
                .withArgument("x-message-ttl", 86400000)
                .build();
    }

    /**
     * 死信队列（处理失败的消息）
     */
    @Bean
    public Queue ticketDlq(){
        return QueueBuilder
                .durable("q.ticket.dlq")
                .build();
    }

    // ==================== Binding（绑定） ====================

    /**
     * 绑定：工单分配队列 ← ticket.assigned → 交换机
     */
    @Bean
    public Binding bindingTicketAssigned(Queue ticketAssignedQueue,
                                         TopicExchange ticketExchange) {
        return BindingBuilder
                .bind(ticketAssignedQueue)
                .to(ticketExchange)
                .with("ticket.assigned");
    }

    /**
     * 绑定：负向情绪队列 ← ticket.emotion.* → 交换机
     */
    @Bean
    public Binding bindingNegativeEmotion(Queue negativeEmotionQueue,
                                         TopicExchange ticketExchange) {
        return BindingBuilder
                .bind(negativeEmotionQueue)
                .to(ticketExchange)
                .with("ticket.emotion.*");
    }

    /**
     * 绑定：死信队列 ← ticket.dlq → 交换机
     */
    @Bean
    public Binding bindingDlq(Queue ticketDlq, TopicExchange ticketExchange) {
        return BindingBuilder
                .bind(ticketDlq)
                .to(ticketExchange)
                .with("ticket.dlq");
    }

    // ==================== MessageConverter（消息转换器） ====================

    /**
     * JSON消息转换器（将Java对象序列化为JSON）
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate配置（生产者使用）
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        // 把 Java 对象自动转成 JSON
        template.setMessageConverter(new Jackson2JsonMessageConverter());
        // 消息发送确认回调
        //必须配合 application.yml 开启 spring.rabbitmq.publisher-confirms: true
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                System.out.println("消息发送成功: " + correlationData);
            } else {
                System.err.println("消息发送失败: " + cause);
            }
        });

        // 消息返回回调（路由失败时触发） -- 找不到队列
        template.setReturnsCallback(returned -> {
            System.err.println("消息路由失败: " + returned.getMessage());
        });
        return template;
    }

    /**
     * 消费者监听器工厂配置
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(new Jackson2JsonMessageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL); // 手动ACK
        factory.setConcurrentConsumers(3); // 并发消费者数
        factory.setMaxConcurrentConsumers(10); // 最大并发消费者数
        factory.setPrefetchCount(10); // 预取数量
        return factory;
    }
}
