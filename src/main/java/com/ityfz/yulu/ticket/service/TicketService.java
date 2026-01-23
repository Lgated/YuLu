package com.ityfz.yulu.ticket.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ityfz.yulu.ticket.entity.Ticket;
import com.ityfz.yulu.ticket.entity.TicketComment;
import com.ityfz.yulu.ticket.enums.TicketStatsResponse;

import java.util.List;

/**
 * 工单服务接口
 */
public interface TicketService {

    /**
     * 当检测到负向情绪时，自动创建工单
     *
     * @param tenantId 租户ID
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param question 用户问题（作为工单描述）
     * @param priority 优先级（可为null，默认MEDIUM）
     * @return 创建的工单
     */
    Ticket createTicketOnNegative(Long tenantId, Long userId, Long sessionId,
                                  String question, String priority);

    /**
     * 按租户和状态分页查询工单列表
     *
     * @param tenantId 租户ID
     * @param status 状态（可为null，表示查询所有状态）
     * @param page 页码（从1开始）
     * @param size 每页大小
     * @return 分页结果
     */
    IPage<Ticket> listTickets(Long tenantId, String status, int page, int size);

    /**
     * 分配工单给指定用户（管理员操作）
     *
     * @param tenantId 租户ID
     * @param ticketId 工单ID
     * @param assigneeUserId 被分配的用户ID（客服或管理员）
     */
    void assign(Long tenantId, Long ticketId, Long assigneeUserId);

    /**
     * 工单状态转移
     * @param tenantId 租户ID
     * @param userId 用户ID
     * @param role 用户角色（ADMIN / AGENT）
     * @param ticketId 工单ID
     * @param targetStatus 目标状态（DONE / CLOSED）
     * @param comment 备注信息
     */
    void transitionStatus(Long tenantId, Long userId, String role,
                          Long ticketId, String targetStatus, String comment);

    /**
     * 添加工单备注
     * @param tenantId 租户ID
     * @param userId 用户ID
     * @param ticketId 工单ID
     * @param content 备注内容
     */
    void addComment(Long tenantId, Long userId, Long ticketId, String content);

    /**
     * 获取工单备注列表
     * @param tenantId 租户ID
     * @param ticketId 工单ID
     * @return
     */
    List<TicketComment> listComments(Long tenantId, Long ticketId);

    /**
     * 统计信息
     * @param tenantId 租户ID
     * @return
     */
    TicketStatsResponse stats(Long tenantId);

    /**
     * 按租户、状态和分配人分页查询工单列表（客服使用）
     *
     * @param tenantId 租户ID
     * @param assigneeId 分配人ID（客服ID），如果为null则查询未分配的工单
     * @param status 状态（可为null，表示查询所有状态）
     * @param page 页码（从1开始）
     * @param size 每页大小
     * @return 分页结果
     */
    IPage<Ticket> listTicketsByAssignee(Long tenantId, Long assigneeId, String status, int page, int size);

    /**
     * 管理员查询所有工单（包括未分配和已分配的）
     *
     * @param tenantId 租户ID
     * @param status 状态（可为null）
     * @param assigneeId 分配人ID（可为null，表示查询所有）
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    IPage<Ticket> listAllTickets(Long tenantId, String status, Long assigneeId, int page, int size);
}
