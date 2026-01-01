package com.ityfz.yulu.ticket.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ityfz.yulu.common.model.ApiResponse;
import com.ityfz.yulu.common.security.SecurityUtil;
import com.ityfz.yulu.ticket.dto.NotifyListRequest;
import com.ityfz.yulu.ticket.dto.NotifyReadRequest;
import com.ityfz.yulu.ticket.entity.NotifyMessage;
import com.ityfz.yulu.ticket.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/notify")
@RequiredArgsConstructor
@Validated
public class NotifyController {

    private final NotificationService notificationService;

    @GetMapping("/list")
    public ApiResponse<IPage<NotifyMessage>> list(NotifyListRequest req) {
        Long tenantId = SecurityUtil.currentTenantId();
        Long userId = SecurityUtil.currentUserId();
        IPage<NotifyMessage> page = notificationService.list(tenantId, userId, req);
        return ApiResponse.success("OK", page);
    }

    @PostMapping("/read")
    public ApiResponse<Void> markRead(@Valid @RequestBody NotifyReadRequest req) {
        Long tenantId = SecurityUtil.currentTenantId();
        Long userId = SecurityUtil.currentUserId();
        notificationService.markRead(tenantId, userId, req.getIds(), Boolean.TRUE.equals(req.getAll()));
        return ApiResponse.success("已读成功", null);
    }
}
