package com.ityfz.yulu.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notify_message")
public class NotifyMessage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;      // 收件人
    private String type;      // 如 TICKET_ASSIGNED
    private String title;
    private String content;
    private Integer readFlag; // 0 未读 1 已读
    private LocalDateTime createTime;
    private LocalDateTime updateTime;


}
