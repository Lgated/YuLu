package com.ityfz.yulu.handoff.vo;

import lombok.Data;

// 客服评价待处理VO
@Data
public class HandoffRatingPendingVO {
    private Boolean needRating;
    private Long handoffRequestId;
    private Long sessionId;
    private Long agentId;
}
