package com.ityfz.yulu.faq.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FaqCategoryVO {

    private Long id;
    private String name;
    private Integer sort;
}
