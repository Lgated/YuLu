package com.ityfz.yulu.faq.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class AdminFaqItemSaveDTO {
    @NotNull
    private Long categoryId;

    @NotBlank
    private String question;

    @NotBlank
    private String answer;

    private String keywords;
    private Integer sort = 100;
    private Integer status = 1;
}
