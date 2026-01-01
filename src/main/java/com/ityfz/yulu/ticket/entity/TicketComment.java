package com.ityfz.yulu.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单跟进记录实体类
 */
@Data
@TableName("ticket_comment")
public class TicketComment {
    /**
     * 记录ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 工单ID
     */
    private Long ticketId;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 操作人ID（谁添加的这条跟进记录）
     */
    private Long userId;

    /**
     * 跟进内容
     */
    private String content;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
