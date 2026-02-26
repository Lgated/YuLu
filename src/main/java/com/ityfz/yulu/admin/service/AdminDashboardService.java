package com.ityfz.yulu.admin.service;


import com.ityfz.yulu.admin.vo.DashboardOverviewVO;

public interface AdminDashboardService {
    DashboardOverviewVO getOverview(Long tenantId, Integer days);
}
