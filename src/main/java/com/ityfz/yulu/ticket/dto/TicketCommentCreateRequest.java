package com.ityfz.yulu.ticket.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class TicketCommentCreateRequest {
    @NotNull
    private Long ticketId;
    @NotBlank
    private String content;
}
