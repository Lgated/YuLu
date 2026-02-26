package com.ityfz.yulu.handoff.vo;


import lombok.Data;

@Data
public class HandoffRatingStatsVO {
    private Long total;
    private Double avgScore;
    private Long positiveCount;
    private Long neutralCount;
    private Long negativeCount;
    private Double positiveRate;
}
