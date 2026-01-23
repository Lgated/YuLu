package com.ityfz.yulu.admin.controller;


import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ityfz.yulu.common.annotation.RequireRole;
import com.ityfz.yulu.common.enums.ErrorCodes;
import com.ityfz.yulu.common.enums.Roles;
import com.ityfz.yulu.common.exception.BizException;
import com.ityfz.yulu.common.model.ApiResponse;
import com.ityfz.yulu.common.security.SecurityUtil;
import com.ityfz.yulu.ticket.dto.TicketAssignRequest;
import com.ityfz.yulu.ticket.dto.TicketCommentCreateRequest;
import com.ityfz.yulu.ticket.dto.TicketTransitionRequest;
import com.ityfz.yulu.ticket.entity.Ticket;
import com.ityfz.yulu.ticket.entity.TicketComment;
import com.ityfz.yulu.ticket.enums.TicketStatsResponse;
import com.ityfz.yulu.ticket.service.TicketService;
import com.ityfz.yulu.user.entity.User;
import com.ityfz.yulu.user.mapper.UserMapper;
import com.ityfz.yulu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * B端工单管理Controller
 * 面向租户的管理员和客服使用
 * 权限要求：ADMIN 或 AGENT
 */
@RestController
@RequestMapping("/api/admin/ticket")
@RequiredArgsConstructor
@Validated
@RequireRole({"ADMIN", "AGENT"})  // 管理员或客服都可以访问
public class AdminTicketController {
    private final TicketService ticketService;
    private final UserService userService;

    /**
     * 分页查询工单列表
     * GET /api/admin/ticket/list?status=PENDING&page=1&size=10
     */
    @GetMapping("/list")
    public ApiResponse<IPage<Ticket>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Long tenantId = SecurityUtil.currentTenantId();
        Long userId = SecurityUtil.currentUserId();
        String role = SecurityUtil.currentRole();

        if (tenantId == null || userId == null) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "请先登录");
        }

        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 10;

        IPage<Ticket> result;

        if (Roles.isAgent(role)) {
            // 客服：只能看到分配给自己的工单
            result = ticketService.listTicketsByAssignee(tenantId, userId, status, page, size);
        } else if (Roles.isAdmin(role)) {
            // 管理员：可以看到所有工单，可以按assigneeId筛选
            result = ticketService.listAllTickets(tenantId, status, assigneeId, page, size);
        } else {
            throw new BizException(ErrorCodes.FORBIDDEN, "无权限访问");
        }

        return ApiResponse.success("查询成功", result);
    }


    /**
     * 获取租户下的客服列表（管理员派单时使用）
     */
    @GetMapping("/agents")
    @RequireRole("ADMIN") // 仅管理员可查看客服列表
    public ApiResponse<List<User>> listAgents() {
        Long tenantId = SecurityUtil.currentTenantId();
        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "缺少租户信息");
        }

        // 查询该租户下所有客服（AGENT角色）
        List<User> agents = userService.listByTenantIdAndRole(tenantId, Roles.AGENT);

        return ApiResponse.success("查询成功", agents);
    }

    /**
     * 分配工单（仅管理员）
     * POST /api/admin/ticket/assign
     */
    @PostMapping("/assign")
    @RequireRole("ADMIN")  // 方法级别：仅管理员可分配工单
    public ApiResponse<Void> assign(@Valid @RequestBody TicketAssignRequest request) {
        Long tenantId = SecurityUtil.currentTenantId();
        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "缺少租户信息，请先登录");
        }

        ticketService.assign(tenantId, request.getTicketId(), request.getAssigneeUserId());
        return ApiResponse.success("工单分配成功", null);
    }

    /**
     * 工单状态流转
     * POST /api/admin/ticket/transition
     */
    @PostMapping("/transition")
    public ApiResponse<Void> transition(@Valid @RequestBody TicketTransitionRequest req) {
        Long tenantId = SecurityUtil.currentTenantId();
        Long userId = SecurityUtil.currentUserId();
        String role = SecurityUtil.currentRole();

        if (tenantId == null || userId == null) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "请先登录");
        }

        ticketService.transitionStatus(tenantId, userId, role,
                req.getTicketId(), req.getTargetStatus(), req.getComment());
        return ApiResponse.success("状态更新成功", null);
    }

    /**
     * 添加工单备注
     * POST /api/admin/ticket/comment
     */
    @PostMapping("/comment")
    public ApiResponse<Void> addComment(@Valid @RequestBody TicketCommentCreateRequest req) {
        Long tenantId = SecurityUtil.currentTenantId();
        Long userId = SecurityUtil.currentUserId();

        if (tenantId == null || userId == null) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "请先登录");
        }

        ticketService.addComment(tenantId, userId, req.getTicketId(), req.getContent());
        return ApiResponse.success("添加成功", null);
    }

    /**
     * 获取工单备注列表
     * GET /api/admin/ticket/comment/list?ticketId=xxx
     */
    @GetMapping("/comment/list")
    public ApiResponse<List<TicketComment>> listComment(@RequestParam Long ticketId) {
        Long tenantId = SecurityUtil.currentTenantId();
        Long userId = SecurityUtil.currentUserId();

        if (tenantId == null || userId == null) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "请先登录");
        }

        return ApiResponse.success("OK", ticketService.listComments(tenantId, ticketId));
    }

    /**
     * 获取工单统计信息
     * GET /api/admin/ticket/stats
     */
    @GetMapping("/stats")
    public ApiResponse<TicketStatsResponse> stats() {
        Long tenantId = SecurityUtil.currentTenantId();
        Long userId = SecurityUtil.currentUserId();

        if (tenantId == null || userId == null) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "请先登录");
        }

        return ApiResponse.success("OK", ticketService.stats(tenantId));
    }

}
