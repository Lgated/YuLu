package com.ityfz.yulu.admin.vo;

import lombok.Data;

import java.util.List;

@Data
public class DashboardOverviewVO {

    private DashboardKpiVO kpi;
    private List<DashboardTrendPointVO> trend;
}