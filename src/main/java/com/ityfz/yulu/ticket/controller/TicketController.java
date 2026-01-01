package com.ityfz.yulu.ticket.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ityfz.yulu.common.enums.ErrorCodes;
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
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/ticket")
@RequiredArgsConstructor
@Validated
public class TicketController {
    private final TicketService ticketService;


    /**
     * 分页查询工单列表
     * GET /api/ticket/list?status=PENDING&page=1&size=10
     *
     * @param status 工单状态（可选，不传则查询所有状态）
     * @param page 页码（默认1）
     * @param size 每页大小（默认10）
     * @return 分页结果
     */
    @GetMapping("/list")
    public ApiResponse<IPage<Ticket>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        // 1. 获取当前登录用户的租户ID和用户ID
        Long tenantId = SecurityUtil.currentTenantId();
        Long userId = SecurityUtil.currentUserId();

        if (tenantId == null || userId == null) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "请先登录");
        }

        // 2. 参数校验
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 100) {
            size = 10; // 限制每页最大100条
        }

        // 3. 调用服务层查询
        IPage<Ticket> result = ticketService.listTickets(tenantId, status, page, size);

        return ApiResponse.success("查询成功", result);
    }

    /**
     * 分配工单给指定用户（仅管理员可操作）
     * POST /api/ticket/assign
     * Body: { "ticketId": 1, "assigneeUserId": 2002 }
     *
     * @param request 分配请求
     * @return 成功响应
     */
    @PostMapping("/assign")
    public ApiResponse<Void> assign(@Valid @RequestBody TicketAssignRequest request) {
        // 1.权限校验：必须是管理员
        SecurityUtil.checkAdmin();

        // 2.获取当前租户ID
        Long tenantId = SecurityUtil.currentTenantId();
        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "缺少租户信息，请先登录");
        }

        // 3.分配工单
        ticketService.assign(tenantId, request.getTicketId(), request.getAssigneeUserId());

        return ApiResponse.success("工单分配成功", null);
    }

    /**
     * 工单状态流转
     * @param req
     * @return
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
     * @param req
     * @return
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
     * @param ticketId 工单ID
     * @return
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
     * @return
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
