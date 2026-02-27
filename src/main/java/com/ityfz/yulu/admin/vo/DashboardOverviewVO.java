package com.ityfz.yulu.admin.vo;

import com.ityfz.yulu.handoff.vo.HandoffLowScoreVO;
import lombok.Data;

import java.util.List;

@Data
public class DashboardOverviewVO {

    private DashboardKpiVO kpi;
    private List<DashboardTrendPointVO> trend;

    // 新增：意图分布
    private List<DashboardIntentStatVO> intentDistribution;
    private List<HandoffLowScoreVO> lowScoreAlerts;
}