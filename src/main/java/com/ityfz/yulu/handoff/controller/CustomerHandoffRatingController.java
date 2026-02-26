package com.ityfz.yulu.handoff.controller;


import com.ityfz.yulu.common.annotation.RequireRole;
import com.ityfz.yulu.common.model.ApiResponse;
import com.ityfz.yulu.common.security.SecurityUtil;
import com.ityfz.yulu.handoff.dto.HandoffRatingSubmitDTO;
import com.ityfz.yulu.handoff.service.HandoffRatingService;
import com.ityfz.yulu.handoff.vo.HandoffRatingPendingVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/customer/handoff/rating")
@RequiredArgsConstructor
@Validated
@RequireRole({"USER"})
@Tag(name = "C端-转人工评价（Customer/HandoffRating）", description = "用户端满意度评价")
public class CustomerHandoffRatingController {

    private final HandoffRatingService handoffRatingService;

    @GetMapping("/pending")
    @Operation(summary = "查询待评价", description = "根据会话查询是否有待评价记录")
    public ApiResponse<HandoffRatingPendingVO> pending(@RequestParam Long sessionId) {
        Long tenantId = SecurityUtil.currentTenantId();
        Long userId = SecurityUtil.currentUserId();
        return ApiResponse.success(handoffRatingService.pending(tenantId, userId, sessionId));
    }

    @PostMapping("/submit")
    @Operation(summary = "提交评价")
    public ApiResponse<Void> submit(@Valid @RequestBody HandoffRatingSubmitDTO dto) {
        Long tenantId = SecurityUtil.currentTenantId();
        Long userId = SecurityUtil.currentUserId();
        handoffRatingService.submit(tenantId, userId, dto);
        return ApiResponse.success("评价提交成功");
    }
}