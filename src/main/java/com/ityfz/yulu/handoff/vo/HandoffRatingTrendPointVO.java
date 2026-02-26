package com.ityfz.yulu.handoff.vo;

import lombok.Data;

@Data
public class HandoffRatingTrendPointVO {
    /** yyyy-MM-dd */
    private String date;
    private Long ratedCount;
    private Double avgScore;
    private Double positiveRate;
}