package com.ityfz.yulu.faq.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * FAQ反馈表
 */
@Data
@TableName("faq_feedback")
public class FaqFeedback {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long faqId;
    private Long userId;
    private Integer feedbackType; // 1 helpful, 0 unhelpful
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
