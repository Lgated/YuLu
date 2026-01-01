package com.ityfz.yulu.ticket.service;


import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ityfz.yulu.ticket.dto.NotifyListRequest;
import com.ityfz.yulu.ticket.entity.NotifyMessage;

import java.util.List;

public interface NotificationService {

    //通知服务
    void notifyAssignment(Long tenantId, Long assigneeUserId,
                          Long ticketId, String title, String priority);

    //通知列表
    IPage<NotifyMessage> list(Long tenantId, Long userId, NotifyListRequest req);

    //通知标记已读
    void markRead(Long tenantId, Long userId, List<Long> ids, boolean all);
}

