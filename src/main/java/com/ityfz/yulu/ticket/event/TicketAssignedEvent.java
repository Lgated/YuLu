package com.ityfz.yulu.ticket.event;

import lombok.Data;

import java.io.Serializable;

/**
 * 工单分配事件
 */
@Data
// 实现 Serializable → 支持 网络传输（RabbitMQ、Kafka、分布式缓存）。
public class TicketAssignedEvent implements Serializable {
    //序列化版本号，防止类结构变化后反序列化失败。
    private static final long serialVersionUID = 1L;

    private Long tenantId;
    private Long assigneeUserId;
    private Long ticketId;
    private String title;
    private String priority;
    private Long timestamp;

    public TicketAssignedEvent() {
        this.timestamp = System.currentTimeMillis();
    }
}
