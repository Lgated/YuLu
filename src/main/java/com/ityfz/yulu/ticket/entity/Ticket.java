package com.ityfz.yulu.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ticket")
public class Ticket {
    /**
     * 工单ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 用户ID（创建工单的用户）
     */
    private Long userId;

    /**
     * 关联的会话ID
     */
    private Long sessionId;

    /**
     * 工单状态：PENDING, PROCESSING, DONE, CLOSED
     */
    private String status;

    /**
     * 优先级：LOW, MEDIUM, HIGH, URGENT
     */
    private String priority;

    /**
     * 分配给的用户ID（客服或管理员）
     */
    private Long assignee;

    /**
     * 工单标题
     */
    private String title;

    /**
     * 工单描述（用户的问题内容）
     */
    private String description;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
