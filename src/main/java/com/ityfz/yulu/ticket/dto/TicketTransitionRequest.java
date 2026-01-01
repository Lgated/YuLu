package com.ityfz.yulu.ticket.dto;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 工单状态转移请求参数
 */
@Data
public class TicketTransitionRequest {
    @NotNull
    private Long ticketId;
    @NotBlank
    private String targetStatus; // DONE / CLOSED
    private String comment;

}
