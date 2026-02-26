package com.ityfz.yulu.admin.vo;

import lombok.Data;

@Data
public class DashboardKpiVO {

    private Long todaySessionCount;
    private Long todayHandoffCount;
    private Long pendingTicketCount;
    private Long onlineAgentCount;
    private String refreshTime;
}