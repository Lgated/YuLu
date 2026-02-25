package com.ityfz.yulu.faq.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * FAQ分类表
 */
@Data
@TableName("faq_category")
public class FaqCategory {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    private Integer sort;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
