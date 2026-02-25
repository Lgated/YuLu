package com.ityfz.yulu.faq.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class FaqFeedbackDTO {

    @NotNull
    private Long faqId;

    @NotNull
    private Integer feedbackType; // 1 有帮助, 0 无帮助
}
