package com.ityfz.yulu.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ityfz.yulu.common.enums.ErrorCodes;
import com.ityfz.yulu.common.exception.BizException;
import com.ityfz.yulu.ticket.dto.NotifyListRequest;
import com.ityfz.yulu.ticket.entity.NotifyMessage;
import com.ityfz.yulu.ticket.mapper.NotifyMessageMapper;
import com.ityfz.yulu.ticket.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotifyMessageMapper notifyMessageMapper;


    @Override
    @Transactional
    public void notifyAssignment(Long tenantId, Long assigneeUserId, Long ticketId, String title, String priority) {
        LocalDateTime now = LocalDateTime.now();
        NotifyMessage msg = new NotifyMessage();
        msg.setTenantId(tenantId);
        msg.setUserId(assigneeUserId);
        msg.setType("TICKET_ASSIGNED");
        msg.setTitle("工单分配: " + title);
        msg.setContent("您被分配了工单#" + ticketId + "，优先级=" + priority);
        msg.setReadFlag(0); // 默认未读
        msg.setCreateTime(now);
        msg.setUpdateTime(now);
        notifyMessageMapper.insert(msg);
        log.info("[Notify] inserted assignment notify, ticketId={}, toUser={}", ticketId, assigneeUserId);
    }

    // 通知列表
    @Override
    public IPage<NotifyMessage> list(Long tenantId, Long userId, NotifyListRequest req) {
        if(tenantId == null || userId == null ){
            throw new BizException(ErrorCodes.UNAUTHORIZED,"请先登录");
        }
        int page = req.getPage() == null || req.getPage() < 1 ? 1 :req.getPage();
        int size = req.getSize() == null || req.getSize() < 1 || req.getSize() > 100 ? 20 :req.getSize();
        LambdaQueryWrapper<NotifyMessage> qw = new LambdaQueryWrapper<NotifyMessage>()
                .eq(NotifyMessage::getTenantId,tenantId)
                .eq(NotifyMessage::getUserId,userId)
                .orderByDesc(NotifyMessage::getCreateTime);
        // 只查未读
        if (Boolean.TRUE.equals(req.getOnlyUnread())) {
            qw.eq(NotifyMessage::getReadFlag, 0);
        }
        return notifyMessageMapper.selectPage(new Page<>(page, size), qw);

    }

    // 通知标记已读
    @Override
    @Transactional
    public void markRead(Long tenantId, Long userId, List<Long> ids, boolean all) {
        // 校验租户和用户
        if(tenantId == null || userId == null ){
            throw new BizException(ErrorCodes.UNAUTHORIZED,"请先登录");
        }

        // 前端说“不是全部已读”，却又不给具体 id 列表 → 参数错误，抛 “ids 不能为空”
        if (!all && CollectionUtils.isEmpty(ids)) {
            throw new BizException(ErrorCodes.VALIDATION_ERROR, "ids 不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<NotifyMessage> qw = new LambdaQueryWrapper<NotifyMessage>()
                .eq(NotifyMessage::getTenantId, tenantId)
                .eq(NotifyMessage::getUserId, userId);
        if (!all) {
            //	只把这些 ID 对应的消息标已读
            qw.in(NotifyMessage::getId, ids);
        } else {
            // 	把该用户所有未读（read_flag=0）的消息一次性标已读
            qw.eq(NotifyMessage::getReadFlag, 0); // 全部未读置已读
        }
        NotifyMessage upd = new NotifyMessage();
        upd.setReadFlag(1);
        upd.setUpdateTime(now);
        notifyMessageMapper.update(upd, qw);
    }
}
