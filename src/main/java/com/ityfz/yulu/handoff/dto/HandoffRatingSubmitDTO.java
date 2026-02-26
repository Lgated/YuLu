package com.ityfz.yulu.handoff.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

// 用户提交的客服评价DTO
@Data
public class HandoffRatingSubmitDTO {

    @NotNull(message = "handoffRequestId不能为空")
    private Long handoffRequestId;

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最小为1")
    @Max(value = 5, message = "评分最大为5")
    private Integer score;

    @Size(max = 6, message = "标签最多6个")
    private List<String> tags;

    @Size(max = 1000, message = "评价内容最多1000字")
    private String comment;

}
