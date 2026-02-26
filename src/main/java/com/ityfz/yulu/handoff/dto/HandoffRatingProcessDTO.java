package com.ityfz.yulu.handoff.dto;

import lombok.Data;

import javax.validation.constraints.Size;

@Data
public class HandoffRatingProcessDTO {

    @Size(max = 1000, message = "处理备注最多1000字")
    private String note;

}
