package com.ityfz.yulu.admin.vo;

import lombok.Data;

@Data
public class DashboardKpiVO {

    private Long todaySessionCount;
    private Long todayHandoffCount;
    private Long pendingTicketCount;
    private Long onlineAgentCount;
    // 新增：满意度KPI
    private Long ratingTotalCount;
    private Double ratingAvgScore;
    private Double ratingPositiveRate;
    // 意图kpi
    private Long negativeEmotionCount;
    private Double negativeEmotionRate;

    private String refreshTime;

}