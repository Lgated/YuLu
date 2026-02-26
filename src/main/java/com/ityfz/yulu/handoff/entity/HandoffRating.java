package com.ityfz.yulu.handoff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("handoff_rating")
public class HandoffRating {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long handoffRequestId;
    private Long sessionId;
    private Long userId;
    private Long agentId;

    private Integer score;
    private String tagsJson;
    private String comment;

    /** WAITING / RATED / PROCESSED / EXPIRED */
    private String status;

    private Long processedBy;
    private String processedNote;
    private LocalDateTime processedTime;

    private LocalDateTime submitTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
