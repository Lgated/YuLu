package com.ityfz.yulu.admin.dto;

import lombok.Data;

/**
 * 统计数据dto
 */
@Data
public class DashboardStats {
    private Long todayChatCount;      // 今日对话量
    private Double aiResolveRate;     // AI解决率
    private Double transferRate;      // 转人工率
    private Long ticketCount;         // 工单数量
    private Long avgResponseTime;     // 平均响应时间（毫秒）
    private Double satisfactionRate; // 满意度
}
