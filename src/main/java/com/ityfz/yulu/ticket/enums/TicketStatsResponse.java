package com.ityfz.yulu.ticket.enums;

import lombok.Data;

import java.util.Map;

@Data
public class TicketStatsResponse {
    private Map<String, Long> byStatus;
    private Map<String, Long> byPriority;
}
