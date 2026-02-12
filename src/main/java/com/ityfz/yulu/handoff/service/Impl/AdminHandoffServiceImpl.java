package com.ityfz.yulu.handoff.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ityfz.yulu.handoff.dto.HandoffRecordQueryDTO;
import com.ityfz.yulu.handoff.entity.HandoffRequest;
import com.ityfz.yulu.handoff.mapper.HandoffEventMapper;
import com.ityfz.yulu.handoff.mapper.HandoffRequestMapper;
import com.ityfz.yulu.handoff.service.AdminHandoffService;
import com.ityfz.yulu.handoff.vo.HandoffRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminHandoffServiceImpl implements AdminHandoffService {

    private final HandoffRequestMapper handoffRequestMapper;
    private final HandoffEventMapper handoffEventMapper;

    @Override
    public Page<HandoffRecordVO> queryRecords(Long tenantId, HandoffRecordQueryDTO query) {
        LambdaQueryWrapper<HandoffRequest> qw = new LambdaQueryWrapper<HandoffRequest>()
                .eq(HandoffRequest::getTenantId, tenantId)
                .eq(query.getUserId() != null, HandoffRequest::getUserId, query.getUserId())
                .eq(query.getAgentId() != null, HandoffRequest::getAgentId, query.getAgentId())
                .eq(StringUtils.hasText(query.getStatus()), HandoffRequest::getStatus, query.getStatus())
                .ge(query.getStartTime() != null, HandoffRequest::getCreateTime, query.getStartTime())
                .le(query.getEndTime() != null, HandoffRequest::getCreateTime, query.getEndTime())
                .orderByDesc(HandoffRequest::getCreateTime);

        Page<HandoffRequest> page = handoffRequestMapper.selectPage(new Page<>(query.getPageNo(), query.getPageSize()), qw);

        List<HandoffRecordVO> vos = page.getRecords().stream().map(this::convertToVO).collect(Collectors.toList());
        Page<HandoffRecordVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(vos);
        return voPage;
    }

    // 将HandoffRequest转换为HandoffRecordVO，并计算等待时长和聊天时长
    private HandoffRecordVO convertToVO(HandoffRequest req) {
        Long wait = (req.getAcceptedAt() != null) ? Duration.between(req.getCreateTime(), req.getAcceptedAt()).getSeconds() : null;
        Long chat = (req.getCompletedAt() != null && req.getAcceptedAt() != null) ? Duration.between(req.getAcceptedAt(), req.getCompletedAt()).getSeconds() : null;

        return HandoffRecordVO.builder()
                .handoffRequestId(req.getId())
                .sessionId(req.getSessionId())
                .userId(req.getUserId())
                .userName("客户#" + req.getUserId())
                .agentId(req.getAgentId())
                .agentName(req.getAgentId() != null ? "客服#" + req.getAgentId() : "未分配")
                .status(req.getStatus())
                .createdAt(req.getCreateTime())
                .acceptedAt(req.getAcceptedAt())
                .completedAt(req.getCompletedAt())
                .waitDurationSeconds(wait)
                .chatDurationSeconds(chat)
                .build();
    }

}
