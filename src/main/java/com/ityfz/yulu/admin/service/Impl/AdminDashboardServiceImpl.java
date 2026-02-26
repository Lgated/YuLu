package com.ityfz.yulu.admin.service.Impl;

import com.ityfz.yulu.admin.mapper.AdminDashboardMapper;
import com.ityfz.yulu.admin.service.AdminDashboardService;
import com.ityfz.yulu.admin.vo.DashboardKpiVO;
import com.ityfz.yulu.admin.vo.DashboardOverviewVO;
import com.ityfz.yulu.admin.vo.DashboardTrendPointVO;
import com.ityfz.yulu.handoff.service.HandoffRatingService;
import com.ityfz.yulu.handoff.vo.HandoffRatingStatsVO;
import com.ityfz.yulu.user.service.AgentStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final AdminDashboardMapper dashboardMapper;
    private final AgentStatusService agentStatusService;
    private final HandoffRatingService handoffRatingService;


    @Override
    public DashboardOverviewVO getOverview(Long tenantId,Integer days) {
        DashboardOverviewVO overview = new DashboardOverviewVO();
        overview.setKpi(buildKpi(tenantId));
        overview.setTrend(buildTrend(tenantId,days));
        return overview;
    }

    private List<DashboardTrendPointVO> buildTrend(Long tenantId,Integer days) {
        //计算
        LocalDate startDate = LocalDate.now().minusDays(days - 1L);

        // 查最近指定天数的会话数量
        List<DashboardTrendPointVO> sessionRows = dashboardMapper.querySessionTrend(tenantId,startDate);
        // 查最近指定天数的转人工数量
        List<DashboardTrendPointVO> handoffRows = dashboardMapper.queryHandoffTrend(tenantId,startDate);

        //使用 linkedHashMap ： 保证按日期顺序遍历，前端图表X轴不会乱序
        Map<String, DashboardTrendPointVO> merged = new LinkedHashMap<>();


        for (int i = 0; i < days; i++) {
            String d = startDate.plusDays(i).toString();
            DashboardTrendPointVO p = new DashboardTrendPointVO();
            p.setDate(d);
            p.setSessionCount(0L);
            p.setHandoffCount(0L);
            merged.put(d, p);
        }

        if (sessionRows != null) {
            for (DashboardTrendPointVO row : sessionRows) {
                DashboardTrendPointVO p = merged.get(row.getDate());
                if (p != null) {
                    p.setSessionCount(defaultZero(row.getSessionCount()));
                }
            }
        }

        if (handoffRows != null) {
            for (DashboardTrendPointVO row : handoffRows) {
                DashboardTrendPointVO p = merged.get(row.getDate());
                if (p != null) {
                    p.setHandoffCount(defaultZero(row.getHandoffCount()));
                }
            }
        }

        return new ArrayList<>(merged.values());
    }

    // 构建KPI数据
    private DashboardKpiVO buildKpi(Long tenantId) {
        DashboardKpiVO kpi = new DashboardKpiVO();
        kpi.setTodaySessionCount(defaultZero(dashboardMapper.countTodaySession(tenantId)));
        kpi.setTodayHandoffCount(defaultZero(dashboardMapper.countTodayHandoff(tenantId)));
        kpi.setPendingTicketCount(defaultZero(dashboardMapper.countPendingTicket(tenantId)));

        List<Long> onlineAgents = agentStatusService.getOnlineAgents(tenantId);
        kpi.setOnlineAgentCount((long) (onlineAgents == null ? 0 : onlineAgents.size()));

//        计算满意度相关KPI
        HandoffRatingStatsVO ratingStats = handoffRatingService.stats(tenantId);
        kpi.setRatingTotalCount(ratingStats.getTotal() == null ? 0L : ratingStats.getTotal());
        kpi.setRatingAvgScore(ratingStats.getAvgScore() == null ? 0D : ratingStats.getAvgScore());
        kpi.setRatingPositiveRate(ratingStats.getPositiveRate() == null ? 0D : ratingStats.getPositiveRate());

        kpi.setRefreshTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return kpi;
    }


    private long defaultZero(Long v) {
        return v == null ? 0L : v;
    }
}
