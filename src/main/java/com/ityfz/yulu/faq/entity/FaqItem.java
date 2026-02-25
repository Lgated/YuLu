package com.ityfz.yulu.faq.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * FAQ条目表
 */
@Data
@TableName("faq_item")
public class FaqItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long categoryId;
    private String question;
    private String answer;
    private String keywords;
    private Integer sort;
    private Integer status;
    private Long viewCount;
    private Long helpfulCount;
    private Long unhelpfulCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
