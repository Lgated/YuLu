package com.ityfz.yulu.ticket.dto;

import lombok.Data;

import java.util.List;

@Data
public class NotifyReadRequest {
    // ids 和 all 二选一；all=true 表示该用户所有未读置为已读
    private List<Long> ids;
    private Boolean all = Boolean.FALSE;
}
