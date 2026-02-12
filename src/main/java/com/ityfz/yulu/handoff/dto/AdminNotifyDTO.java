package com.ityfz.yulu.handoff.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 广播通知
 */
@Data
public class AdminNotifyDTO {
    @NotBlank(message = "标题不能为空")
    private String title;
    @NotBlank(message = "内容不能为空")
    private String content;

}
