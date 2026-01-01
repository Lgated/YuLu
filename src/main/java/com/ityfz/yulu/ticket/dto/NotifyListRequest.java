package com.ityfz.yulu.ticket.dto;

import lombok.Data;

@Data
public class NotifyListRequest {
    private Boolean onlyUnread = Boolean.TRUE; // 是否只查未读
    private Integer page = 1;
    private Integer size = 20; // 1~100
}
