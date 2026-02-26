package com.ityfz.yulu.admin.vo;

import lombok.Data;

@Data
public class DashboardTrendPointVO {
    /** yyyy-MM-dd */
    private String date;
    private Long sessionCount;
    private Long handoffCount;
}