package com.ityfz.yulu.handoff.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ityfz.yulu.common.enums.ErrorCodes;
import com.ityfz.yulu.common.exception.BizException;
import com.ityfz.yulu.handoff.dto.HandoffRatingProcessDTO;
import com.ityfz.yulu.handoff.dto.HandoffRatingQueryDTO;
import com.ityfz.yulu.handoff.dto.HandoffRatingSubmitDTO;
import com.ityfz.yulu.handoff.entity.HandoffRating;
import com.ityfz.yulu.handoff.mapper.HandoffRatingMapper;
import com.ityfz.yulu.handoff.service.HandoffRatingService;
import com.ityfz.yulu.handoff.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HandoffRatingServiceImpl implements HandoffRatingService {

    private final HandoffRatingMapper ratingMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markWaiting(Long tenantId, Long handoffRequestId, Long sessionId, Long userId, Long agentId) {

        // 幂等：同一会话同一转人工请求只会有一个待评价记录
        LambdaQueryWrapper<HandoffRating> qw = new LambdaQueryWrapper<HandoffRating>()
                .eq(HandoffRating::getTenantId, tenantId)
                .eq(HandoffRating::getHandoffRequestId, handoffRequestId)
                .last("LIMIT 1");

        HandoffRating existed = ratingMapper.selectOne(qw);
        if (existed != null) {
            return;
        }

        HandoffRating rating = new HandoffRating();
        rating.setTenantId(tenantId);
        rating.setHandoffRequestId(handoffRequestId);
        rating.setSessionId(sessionId);
        rating.setUserId(userId);
        rating.setAgentId(agentId);
        rating.setStatus("WAITING");
        rating.setCreateTime(LocalDateTime.now());
        rating.setUpdateTime(LocalDateTime.now());
        ratingMapper.insert(rating);
    }

    @Override
    public HandoffRatingPendingVO pending(Long tenantId, Long userId, Long sessionId) {

        LambdaQueryWrapper<HandoffRating> qw = new LambdaQueryWrapper<HandoffRating>()
                .eq(HandoffRating::getTenantId, tenantId)
                .eq(HandoffRating::getUserId, userId)
                .eq(HandoffRating::getSessionId, sessionId)
                .eq(HandoffRating::getStatus, "WAITING")
                .orderByDesc(HandoffRating::getCreateTime)
                .last("LIMIT 1");

        HandoffRating rating = ratingMapper.selectOne(qw);

        HandoffRatingPendingVO vo = new HandoffRatingPendingVO();
        if (rating == null) {
            vo.setNeedRating(false);
            return vo;
        }

        vo.setNeedRating(true);
        vo.setHandoffRequestId(rating.getHandoffRequestId());
        vo.setSessionId(rating.getSessionId());
        vo.setAgentId(rating.getAgentId());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long tenantId, Long userId, HandoffRatingSubmitDTO dto) {

        // 验证待评价记录存在且状态为WAITING
        LambdaQueryWrapper<HandoffRating> qw = new LambdaQueryWrapper<HandoffRating>()
                .eq(HandoffRating::getTenantId, tenantId)
                .eq(HandoffRating::getUserId, userId)
                .eq(HandoffRating::getHandoffRequestId, dto.getHandoffRequestId())
                .last("LIMIT 1");

        HandoffRating rating = ratingMapper.selectOne(qw);
        if (rating == null) {
            throw new BizException(ErrorCodes.NOT_FOUND, "待评价记录不存在");
        }
        if (!"WAITING".equals(rating.getStatus())) {
            throw new BizException(ErrorCodes.BIZ_ERROR, "该记录已评价或不可评价");
        }

        rating.setScore(dto.getScore());
        rating.setComment(dto.getComment());
        rating.setTagsJson(toJson(dto.getTags()));
        rating.setStatus("RATED");
        rating.setSubmitTime(LocalDateTime.now());
        rating.setUpdateTime(LocalDateTime.now());

        ratingMapper.updateById(rating);
    }

    @Override
    public Page<HandoffRatingRecordVO> page(Long tenantId, HandoffRatingQueryDTO query) {
        LambdaQueryWrapper<HandoffRating> qw = new LambdaQueryWrapper<HandoffRating>()
                .eq(HandoffRating::getTenantId, tenantId)
                .eq(query.getAgentId() != null, HandoffRating::getAgentId, query.getAgentId())
                .eq(query.getScore() != null, HandoffRating::getScore, query.getScore())
                .eq(StringUtils.hasText(query.getStatus()), HandoffRating::getStatus, query.getStatus())
                .ge(query.getStartTime() != null, HandoffRating::getCreateTime, query.getStartTime())
                .le(query.getEndTime() != null, HandoffRating::getCreateTime, query.getEndTime())
                .orderByDesc(HandoffRating::getCreateTime);

        Page<HandoffRating> page = ratingMapper.selectPage(new Page<>(query.getPageNo(), query.getPageSize()), qw);

        List<HandoffRatingRecordVO> records = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());

        Page<HandoffRatingRecordVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(records);
        return voPage;
    }

    @Override
    public HandoffRatingStatsVO stats(Long tenantId) {
        Long total = defaultZero(ratingMapper.countRated(tenantId));
        Double avg = ratingMapper.avgScore(tenantId);
        Long positive = defaultZero(ratingMapper.countPositive(tenantId));
        Long neutral = defaultZero(ratingMapper.countNeutral(tenantId));
        Long negative = defaultZero(ratingMapper.countNegative(tenantId));

        HandoffRatingStatsVO vo = new HandoffRatingStatsVO();
        vo.setTotal(total);
        vo.setAvgScore(avg == null ? 0D : avg);
        vo.setPositiveCount(positive);
        vo.setNeutralCount(neutral);
        vo.setNegativeCount(negative);

        if (total == 0) {
            vo.setPositiveRate(0D);
        } else {
            vo.setPositiveRate(positive * 100.0 / total);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void process(Long tenantId, Long ratingId, Long adminId, HandoffRatingProcessDTO dto) {
        HandoffRating rating = ratingMapper.selectById(ratingId);
        if (rating == null || !tenantId.equals(rating.getTenantId())) {
            throw new BizException(ErrorCodes.NOT_FOUND, "评价记录不存在");
        }
        if (!("RATED".equals(rating.getStatus()) || "PROCESSED".equals(rating.getStatus()))) {
            throw new BizException(ErrorCodes.BIZ_ERROR, "当前状态不允许处理");
        }

        rating.setStatus("PROCESSED");
        rating.setProcessedBy(adminId);
        rating.setProcessedNote(dto == null ? null : dto.getNote());
        rating.setProcessedTime(LocalDateTime.now());
        rating.setUpdateTime(LocalDateTime.now());
        ratingMapper.updateById(rating);
    }

    @Override
    public List<HandoffRatingTrendPointVO> trend(Long tenantId, Integer days) {
        int safeDays = (days == null || (days != 7 && days != 30 && days != 90)) ? 7 : days;
        LocalDate startDate = LocalDate.now().minusDays(safeDays - 1L);

        List<HandoffRatingTrendPointVO> rows = ratingMapper.queryRatingTrend(tenantId, startDate);
        Map<String, HandoffRatingTrendPointVO> merged = new LinkedHashMap<>();

        for (int i = 0; i < safeDays; i++) {
            String d = startDate.plusDays(i).toString();
            HandoffRatingTrendPointVO p = new HandoffRatingTrendPointVO();
            p.setDate(d);
            p.setRatedCount(0L);
            p.setAvgScore(0D);
            p.setPositiveRate(0D);
            merged.put(d, p);
        }

        if (rows != null) {
            for (HandoffRatingTrendPointVO row : rows) {
                HandoffRatingTrendPointVO p = merged.get(row.getDate());
                if (p != null) {
                    p.setRatedCount(row.getRatedCount() == null ? 0L : row.getRatedCount());
                    p.setAvgScore(row.getAvgScore() == null ? 0D : row.getAvgScore());
                    p.setPositiveRate(row.getPositiveRate() == null ? 0D : row.getPositiveRate());
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    @Override
    public List<HandoffLowScoreVO> lowScoreTop(Long tenantId, Integer days, Integer limit, Integer maxScore) {
        int safeDays = (days == null || days <= 0) ? 7 : days;
        int safeLimit = (limit == null || limit <= 0 || limit > 100) ? 10 : limit;
        int safeMaxScore = (maxScore == null || maxScore < 1 || maxScore > 5) ? 2 : maxScore;

        LocalDateTime startTime = LocalDate.now().minusDays(safeDays - 1L).atStartOfDay();
        return ratingMapper.queryLowScoreTop(tenantId, startTime, safeMaxScore, safeLimit);
    }

    private String toJson(List<String> tags) {
        if (tags == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCodes.BIZ_ERROR, "标签序列化失败");
        }
    }

    private HandoffRatingRecordVO toVO(HandoffRating e) {
        HandoffRatingRecordVO vo = new HandoffRatingRecordVO();
        vo.setId(e.getId());
        vo.setHandoffRequestId(e.getHandoffRequestId());
        vo.setSessionId(e.getSessionId());
        vo.setUserId(e.getUserId());
        vo.setAgentId(e.getAgentId());
        vo.setScore(e.getScore());
        vo.setTags(parseTags(e.getTagsJson()));
        vo.setComment(e.getComment());
        vo.setStatus(e.getStatus());
        vo.setSubmitTime(e.getSubmitTime());
        vo.setProcessedBy(e.getProcessedBy());
        vo.setProcessedNote(e.getProcessedNote());
        vo.setProcessedTime(e.getProcessedTime());
        return vo;
    }

    // 标签JSON字符串解析为List<String>
    private List<String> parseTags(String tagsJson) {
        if (!StringUtils.hasText(tagsJson)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private Long defaultZero(Long v) {
        return v == null ? 0L : v;
    }
}
