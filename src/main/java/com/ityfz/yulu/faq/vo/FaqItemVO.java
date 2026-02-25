package com.ityfz.yulu.faq.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FaqItemVO {

    private Long id;
    private Long categoryId;
    private String question;
    private String answer;
    private String keywords;
    private Long viewCount;
    private Long helpfulCount;
    private Long unhelpfulCount;
}
