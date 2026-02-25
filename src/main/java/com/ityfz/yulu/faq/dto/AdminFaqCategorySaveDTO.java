package com.ityfz.yulu.faq.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class AdminFaqCategorySaveDTO {
    @NotBlank
    private String name;
    private Integer sort = 100;
    private Integer status = 1;
}


