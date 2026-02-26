package com.ityfz.yulu.handoff.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ityfz.yulu.handoff.dto.HandoffRatingProcessDTO;
import com.ityfz.yulu.handoff.dto.HandoffRatingQueryDTO;
import com.ityfz.yulu.handoff.dto.HandoffRatingSubmitDTO;
import com.ityfz.yulu.handoff.vo.HandoffRatingPendingVO;
import com.ityfz.yulu.handoff.vo.HandoffRatingRecordVO;
import com.ityfz.yulu.handoff.vo.HandoffRatingStatsVO;

public interface HandoffRatingService {

    /** 在转人工结束时标记待评价（幂等） */
    void markWaiting(Long tenantId, Long handoffRequestId, Long sessionId, Long userId, Long agentId);

    /** 用户查询当前会话是否需要评价 */
    HandoffRatingPendingVO pending(Long tenantId, Long userId, Long sessionId);

    /** 用户提交评价 */
    void submit(Long tenantId, Long userId, HandoffRatingSubmitDTO dto);

    /** 管理端分页查询 */
    Page<HandoffRatingRecordVO> page(Long tenantId, HandoffRatingQueryDTO query);

    /** 管理端统计 */
    HandoffRatingStatsVO stats(Long tenantId);

    /** 管理端处理反馈 */
    void process(Long tenantId, Long ratingId, Long adminId, HandoffRatingProcessDTO dto);

}
