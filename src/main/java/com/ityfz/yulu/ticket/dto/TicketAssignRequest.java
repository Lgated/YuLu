package com.ityfz.yulu.ticket.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 工单分配请求DTO
 */
@Data
public class TicketAssignRequest {

    /**
     * 工单ID
     */
    @NotNull(message = "工单ID不能为空")
    private Long ticketId;

    /**
     * 被分配的用户ID（客服或管理员）
     */
    @NotNull(message = "被分配人ID不能为空")
    private Long assigneeUserId;

}
