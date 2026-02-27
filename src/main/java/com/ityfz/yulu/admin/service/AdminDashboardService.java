package com.ityfz.yulu.admin.service;


import com.ityfz.yulu.admin.vo.DashboardIntentStatVO;
import com.ityfz.yulu.admin.vo.DashboardOverviewVO;
import com.ityfz.yulu.handoff.vo.HandoffLowScoreVO;

import java.util.List;

public interface AdminDashboardService {
    DashboardOverviewVO getOverview(Long tenantId, Integer days);

    // 意图分布
    List<DashboardIntentStatVO> getIntentDistribution(Long tenantId, Integer days);

    // 低分告警
    List<HandoffLowScoreVO> getLowScoreAlerts(Long tenantId, Integer days, Integer limit);

    // 负向情绪率（百分比）
    Double getNegativeEmotionRate(Long tenantId, Integer days);
}
