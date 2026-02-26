package com.ityfz.yulu.handoff.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ityfz.yulu.common.annotation.RequireRole;
import com.ityfz.yulu.common.model.ApiResponse;
import com.ityfz.yulu.common.security.SecurityUtil;
import com.ityfz.yulu.handoff.dto.HandoffRatingProcessDTO;
import com.ityfz.yulu.handoff.dto.HandoffRatingQueryDTO;
import com.ityfz.yulu.handoff.service.HandoffRatingService;
import com.ityfz.yulu.handoff.vo.HandoffLowScoreVO;
import com.ityfz.yulu.handoff.vo.HandoffRatingRecordVO;
import com.ityfz.yulu.handoff.vo.HandoffRatingStatsVO;
import com.ityfz.yulu.handoff.vo.HandoffRatingTrendPointVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/admin/handoff/rating")
@RequiredArgsConstructor
@Validated
@RequireRole({"ADMIN"})
@Tag(name = "B端-转人工评价管理（Admin/HandoffRating）", description = "评价列表、统计、处理")
public class AdminHandoffRatingController {

    private final HandoffRatingService handoffRatingService;

    @GetMapping("/list")
    @Operation(summary = "评价记录分页")
    public ApiResponse<Page<HandoffRatingRecordVO>> list(HandoffRatingQueryDTO query) {
        Long tenantId = SecurityUtil.currentTenantId();
        return ApiResponse.success(handoffRatingService.page(tenantId, query));
    }

    @GetMapping("/stats")
    @Operation(summary = "评价统计")
    public ApiResponse<HandoffRatingStatsVO> stats() {
        Long tenantId = SecurityUtil.currentTenantId();
        return ApiResponse.success(handoffRatingService.stats(tenantId));
    }

    @PostMapping("/{ratingId}/process")
    @Operation(summary = "处理评价反馈")
    public ApiResponse<Void> process(@PathVariable Long ratingId,
                                     @Valid @RequestBody(required = false) HandoffRatingProcessDTO dto) {
        Long tenantId = SecurityUtil.currentTenantId();
        Long adminId = SecurityUtil.currentUserId();
        handoffRatingService.process(tenantId, ratingId, adminId, dto);
        return ApiResponse.success("处理成功");
    }

    @GetMapping("/trend")
    @Operation(summary = "满意度趋势", description = "按天返回评分数、平均分、好评率")
    public ApiResponse<List<HandoffRatingTrendPointVO>> trend(
            @RequestParam(value = "days", defaultValue = "7") Integer days) {
        Long tenantId = SecurityUtil.currentTenantId();
        return ApiResponse.success(handoffRatingService.trend(tenantId, days));
    }

    @GetMapping("/low-score/top")
    @Operation(summary = "低分评价TopN", description = "用于运营告警卡片")
    public ApiResponse<List<HandoffLowScoreVO>> lowScoreTop(
            @RequestParam(value = "days", defaultValue = "7") Integer days,
            @RequestParam(value = "limit", defaultValue = "10") Integer limit,
            @RequestParam(value = "maxScore", defaultValue = "2") Integer maxScore) {
        Long tenantId = SecurityUtil.currentTenantId();
        return ApiResponse.success(handoffRatingService.lowScoreTop(tenantId, days, limit, maxScore));
    }
}
