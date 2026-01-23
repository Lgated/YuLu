package com.ityfz.yulu.ticket.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ityfz.yulu.common.model.ApiResponse;
import com.ityfz.yulu.common.security.SecurityUtil;
import com.ityfz.yulu.ticket.dto.NotifyListRequest;
import com.ityfz.yulu.ticket.dto.NotifyReadRequest;
import com.ityfz.yulu.ticket.entity.NotifyMessage;
import com.ityfz.yulu.ticket.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;


@RestController
@RequestMapping("/api/notify")
@RequiredArgsConstructor
@Validated
@Tag(name = "通知（Notify）", description = "通知中心：分页查询、标记已读")
public class NotifyController {

    private final NotificationService notificationService;

    /**
     * 分页查询通知列表
     */
    @GetMapping("/list")
    @Operation(summary = "分页查询通知列表", description = "查询当前登录用户在当前租户下的通知列表")
    public ApiResponse<IPage<NotifyMessage>> list(NotifyListRequest req) {
        Long tenantId = SecurityUtil.currentTenantId();
        Long userId = SecurityUtil.currentUserId();
        IPage<NotifyMessage> page = notificationService.list(tenantId, userId, req);
        return ApiResponse.success("OK", page);
    }

    /**
     * 标记通知为已读
     */
    @PostMapping("/read")
    @Operation(summary = "标记通知已读", description = "支持按 ids 标记，或 all=true 标记全部已读")
    public ApiResponse<Void> markRead(@Valid @RequestBody NotifyReadRequest req) {
        Long tenantId = SecurityUtil.currentTenantId();
        Long userId = SecurityUtil.currentUserId();
        notificationService.markRead(tenantId, userId, req.getIds(), Boolean.TRUE.equals(req.getAll()));
        return ApiResponse.success("已读成功", null);
    }
}
