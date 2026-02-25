package com.ityfz.yulu.faq.dto;

import lombok.Data;

@Data
public class FaqListQueryDTO {

    private Long categoryId;
    private String keyword;
    private Integer page = 1;
    private Integer size = 10;

}
