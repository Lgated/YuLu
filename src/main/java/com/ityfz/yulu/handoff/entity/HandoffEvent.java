package com.ityfz.yulu.handoff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 转人工事件记录实体类（用于审计）
 */
@Data
@TableName("handoff_event")
public class HandoffEvent {


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
     * 转人工请求ID
     */
    private Long handoffRequestId;

    /**
     * 事件类型：CREATED-创建，ASSIGNED-分配，ACCEPTED-接受，REJECTED-拒绝，STARTED-开始，COMPLETED-完成，CLOSED-关闭
     */
    private String eventType;

    /**
     * 事件数据（JSON格式，存储详细信息）
     */
    private String eventData;

    /**
     * 操作人ID（客户或客服）
     */
    private Long operatorId;

    /**
     * 操作人类型：USER-客户，AGENT-客服，SYSTEM-系统
     */
    private String operatorType;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
