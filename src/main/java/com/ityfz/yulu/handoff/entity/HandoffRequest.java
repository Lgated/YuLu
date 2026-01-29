package com.ityfz.yulu.handoff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 转人工请求实体类
 */
@Data
@TableName("handoff_request")
public class HandoffRequest {


    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 会话ID
     */
    private Long sessionId;

    /**
     * 客户ID
     */
    private Long userId;

    /**
     * 关联工单ID（转人工时自动创建/关联）
     */
    private Long ticketId;

    /**
     * 分配的客服ID
     */
    private Long agentId;

    /**
     * 状态：PENDING-排队中，ASSIGNED-已分配，ACCEPTED-已接受，IN_PROGRESS-进行中，COMPLETED-已完成，CLOSED-已关闭，REJECTED-已拒绝
     */
    private String status;

    /**
     * 优先级：LOW-低，MEDIUM-中，HIGH-高，URGENT-紧急
     */
    private String priority;

    /**
     * 转人工原因（客户填写）
     */
    private String reason;

    /**
     * 排队位置
     */
    private Integer queuePosition;

    /**
     * 分配时间
     */
    private LocalDateTime assignedAt;

    /**
     * 客服接受时间
     */
    private LocalDateTime acceptedAt;

    /**
     * 对话开始时间
     */
    private LocalDateTime startedAt;

    /**
     * 对话完成时间
     */
    private LocalDateTime completedAt;

    /**
     * 关闭时间
     */
    private LocalDateTime closedAt;

    /**
     * 拒绝原因（如果客服拒绝）
     */
    private String rejectReason;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
