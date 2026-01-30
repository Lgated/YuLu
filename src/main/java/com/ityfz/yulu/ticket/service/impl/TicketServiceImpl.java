package com.ityfz.yulu.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ityfz.yulu.common.enums.ErrorCodes;
import com.ityfz.yulu.common.enums.Roles;
import com.ityfz.yulu.common.exception.BizException;
import com.ityfz.yulu.ticket.entity.Ticket;
import com.ityfz.yulu.ticket.entity.TicketComment;
import com.ityfz.yulu.ticket.enums.TicketPriority;
import com.ityfz.yulu.ticket.enums.TicketStatsResponse;
import com.ityfz.yulu.ticket.enums.TicketStatus;
import com.ityfz.yulu.ticket.event.TicketAssignedEvent;
import com.ityfz.yulu.ticket.mapper.TicketCommentMapper;
import com.ityfz.yulu.ticket.mapper.TicketMapper;
import com.ityfz.yulu.ticket.mq.TicketEventPublisher;
import com.ityfz.yulu.ticket.service.NotificationService;
import com.ityfz.yulu.ticket.service.TicketService;
import com.ityfz.yulu.user.entity.User;
import com.ityfz.yulu.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工单服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private final TicketMapper ticketMapper;
    private final TicketCommentMapper ticketCommentMapper;
    private final UserMapper userMapper; // 需构造注入
    private final NotificationService notificationService; // 需构造注入
    private final TicketEventPublisher ticketEventPublisher;

    @Override
    public Ticket createTicketOnNegative(Long tenantId, Long userId, Long sessionId, String question, String priority) {
        //1、参数校验
        if(tenantId == null || userId == null || sessionId == null){
            throw new BizException(ErrorCodes.VALIDATION_ERROR,"租户id、用户id、会话id不能为空");
        }

        //2、确定优先级
        String priorityCode = priority;
        if (!StringUtils.hasText(priorityCode) || !TicketPriority.isValid(priorityCode)) {
            priorityCode = TicketPriority.MEDIUM.getCode();
        }

        //3、构建工单标题（基于会话ID和问题前50个字符）
        String title = "情绪告警-会话#" + sessionId;
        if (StringUtils.hasText(question) && question.length() > 50) {
            title += "-" + question.substring(0, 50);
        }

        // 4. 创建工单实体
        LocalDateTime now = LocalDateTime.now();
        Ticket ticket = new Ticket();
        ticket.setTenantId(tenantId);
        ticket.setUserId(userId);
        ticket.setSessionId(sessionId);
        ticket.setStatus(TicketStatus.PENDING.getCode()); // 初始状态：待处理
        ticket.setPriority(priorityCode);
        ticket.setAssignee(null); // 初始未分配
        ticket.setTitle(title);
        ticket.setDescription(question); // 用户问题作为描述
        ticket.setCreateTime(now);
        ticket.setUpdateTime(now);

        // 5. 保存到数据库
        ticketMapper.insert(ticket);
        log.info("[Ticket] 自动创建工单: tenantId={}, userId={}, sessionId={}, ticketId={}, priority={}",
                tenantId, userId, sessionId, ticket.getId(), priorityCode);

        return ticket;
    }

    //按租户和状态分页查询工单列表
    @Override
    public IPage<Ticket> listTickets(Long tenantId, String status, int page, int size) {
        // 1. 参数校验
        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "租户ID不能为空");
        }


        // 2. 构建查询条件
        LambdaQueryWrapper<Ticket> queryWrapper = Wrappers.<Ticket>lambdaQuery()
                .eq(Ticket::getTenantId, tenantId) // 必须按租户过滤
                .eq(StringUtils.hasText(status) && TicketStatus.isValid(status),
                        Ticket::getStatus, status) // 如果指定了状态且有效，则按状态过滤
                .orderByDesc(Ticket::getUpdateTime); // 按更新时间倒序

        //3、分页查询
        Page<Ticket> pageParam = new Page<>(page, size);
        IPage<Ticket> result = ticketMapper.selectPage(pageParam, queryWrapper);
        log.debug("[Ticket] 查询工单列表: tenantId={}, status={}, page={}, size={}, total={}",
                tenantId, status, page, size, result.getTotal());

        return result;
    }

    @Override
    @Transactional
    public void assign(Long tenantId, Long ticketId, Long assigneeUserId) {
        // 1. 参数校验
        if (tenantId == null || ticketId == null || assigneeUserId == null) {
            throw new BizException(ErrorCodes.VALIDATION_ERROR, "租户ID、工单ID、被分配人ID不能为空");
        }

        // 2. 查询工单
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new BizException(ErrorCodes.TICKET_NOT_FOUND, "工单不存在");
        }

        // 3. 校验租户归属
        if (!tenantId.equals(ticket.getTenantId())) {
            throw new BizException(ErrorCodes.TICKET_NOT_FOUND, "工单不属于当前租户");
        }

        //确认被分配用户是客服或者管理员
        ensureAssigneeIsAgentOrAdmin(tenantId, assigneeUserId);
        // 4. 更新工单（分配并更新状态为处理中）
        ticket.setAssignee(assigneeUserId);
        ticket.setStatus(TicketStatus.PROCESSING.getCode()); // 分配后自动变为处理中
        ticket.setUpdateTime(LocalDateTime.now());

        int updateCount = ticketMapper.updateById(ticket);

        if (updateCount <= 0) {
            throw new BizException(ErrorCodes.SYSTEM_ERROR, "工单分配失败");
        }

        // ========== 新增：发送MQ事件 ==========
        try{
            TicketAssignedEvent event = new TicketAssignedEvent();
            event.setTenantId(tenantId);
            event.setAssigneeUserId(assigneeUserId);
            event.setTicketId(ticketId);
            event.setTitle(ticket.getTitle());
            event.setPriority(ticket.getPriority());
            //推送消息到rabbitmq
            ticketEventPublisher.publishAssigned(event);
        }catch (Exception e){
            // MQ发送失败不影响主流程，记录日志即可
            log.error("[Ticket] MQ事件发送失败，但不影响工单分配: ticketId={}, error={}",
                    ticketId, e.getMessage(), e);
            //mq挂了的话，走降级同步通知来兜底
            notificationService.notifyAssignment(
                    tenantId, assigneeUserId, ticketId, ticket.getTitle(), ticket.getPriority());
        }
        log.info("[Ticket] 工单分配成功: ticketId={}, tenantId={}, assigneeUserId={}",
                ticketId, tenantId, assigneeUserId);

    }


    /**
     * 确保被分配用户是客服或管理员
     */
    private void ensureAssigneeIsAgentOrAdmin(Long tenantId, Long assigneeUserId) {
        User u = userMapper.selectById(assigneeUserId);
        if (u == null || !tenantId.equals(u.getTenantId())) {
            throw new BizException(ErrorCodes.USER_NOT_FOUND, "被分配用户不存在或不属于当前租户");
        }
        if (!Roles.isAdmin(u.getRole()) && !Roles.isAgent(u.getRole())) {
            throw new BizException(ErrorCodes.FORBIDDEN, "被分配人必须是客服或管理员");
        }
    }

    @Override
    public void transitionStatus(Long tenantId, Long userId, String role, Long ticketId, String targetStatus, String comment) {

        // 1. 查询工单&&查询校验
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null || !tenantId.equals(ticket.getTenantId())) {
            throw new BizException(ErrorCodes.TICKET_NOT_FOUND, "工单不存在或不属于当前租户");
        }

        // 2. 目标状态校验
        TicketStatus target = TicketStatus.fromCode(targetStatus);
        if (target == null || target == TicketStatus.PENDING) {
            throw new BizException(ErrorCodes.TICKET_STATUS_INVALID, "目标状态非法");
        }

        // 3. 权限校验
        /*| 当前状态   | 目标状态   | 触发场景/限制                     |
| ---------- | ---------- | --------------------------------- |
| PENDING    | PROCESSING | 分配工单时自动流转                |
| PROCESSING | DONE       | 客服/管理员确认解决               |
| PROCESSING | CLOSED     | 客服/管理员确认关闭（无法处理等） |
| DONE       | CLOSED     | 管理员关闭（后续整理）            |
| PENDING    | CLOSED     | 管理员直接关闭                    |
         */
        TicketStatus current = TicketStatus.fromCode(ticket.getStatus());
        boolean ok = false;
        // 允许管理员/客服手动开始处理：PENDING -> PROCESSING
        if (current == TicketStatus.PENDING && target == TicketStatus.PROCESSING) ok = true;
        if (current == TicketStatus.PROCESSING && (target == TicketStatus.DONE || target == TicketStatus.CLOSED)) ok = true;
        if (current == TicketStatus.DONE && target == TicketStatus.CLOSED) ok = true;
        if (current == TicketStatus.PENDING && target == TicketStatus.CLOSED && Roles.isAdmin(role)) ok = true;
        if (!ok) {
            throw new BizException(ErrorCodes.FORBIDDEN, "当前状态不允许流转到目标状态");
        }
        //4. 更新工单状态&&记录日志
        ticket.setStatus(target.getCode());
        // 如果开始处理且还未分配，则默认把处理人设为当前操作用户（管理员也允许）
        if (current == TicketStatus.PENDING && target == TicketStatus.PROCESSING && ticket.getAssignee() == null) {
            ticket.setAssignee(userId);
        }
        ticket.setUpdateTime(LocalDateTime.now());
        ticketMapper.updateById(ticket);

        //5. 记录备注
        if (StringUtils.hasText(comment)) {
            TicketComment c = new TicketComment();
            c.setTicketId(ticketId);
            c.setTenantId(tenantId);
            c.setUserId(userId);
            c.setContent("[状态流转] " + comment + " -> " + target.getDesc());
            c.setCreateTime(LocalDateTime.now());
            ticketCommentMapper.insert(c);
        }

        log.info("[Ticket] 状态流转: ticketId={}, {} -> {}, by user={}", ticketId, current, target, userId);

    }

    @Override
    @Transactional
    public void addComment(Long tenantId, Long userId, Long ticketId, String content) {
        // 1. 参数校验
        if (tenantId == null || ticketId == null || userId == null) {
            throw new BizException(ErrorCodes.VALIDATION_ERROR, "租户ID、工单ID、用户ID不能为空");
        }
        Ticket t = ticketMapper.selectById(ticketId);
        if (t == null || !tenantId.equals(t.getTenantId())) {
            throw new BizException(ErrorCodes.TICKET_NOT_FOUND, "工单不存在或不属于当前租户");
        }
        TicketComment c = new TicketComment();
        c.setTicketId(ticketId);
        c.setTenantId(tenantId);
        c.setUserId(userId);
        c.setContent(content);
        c.setCreateTime(LocalDateTime.now());
        ticketCommentMapper.insert(c);
    }

    @Override
    public List<TicketComment> listComments(Long tenantId, Long ticketId) {
       return ticketCommentMapper.selectList(
                Wrappers.<TicketComment>lambdaQuery()
                        .eq(TicketComment::getTenantId, tenantId)
                        .eq(TicketComment::getTicketId, ticketId)
                        .orderByAsc(TicketComment::getCreateTime)
        );

    }

    /**
     * 统计工单数量
     */
    @Override
    public TicketStatsResponse stats(Long tenantId) {
        if (tenantId == null) throw new BizException(ErrorCodes.TENANT_REQUIRED, "租户ID不能为空");
        TicketStatsResponse r = new TicketStatsResponse();
        r.setByStatus(toMap(ticketMapper.countByStatus(tenantId), "status"));
        r.setByPriority(toMap(ticketMapper.countByPriority(tenantId), "priority"));
        return r;
    }

    @Override
    public IPage<Ticket> listTicketsByAssignee(Long tenantId, Long assigneeId, String status, int page, int size) {
        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "租户ID不能为空");
        }
        if (assigneeId == null) {
            throw new BizException(ErrorCodes.VALIDATION_ERROR, "分配人ID不能为空");
        }

        Page<Ticket> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Ticket> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Ticket::getTenantId, tenantId)
                .eq(Ticket::getAssignee, assigneeId); // 只查询分配给自己的

        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq(Ticket::getStatus, status);
        }

        // 按照最新创建时间展示
        wrapper.orderByDesc(Ticket::getCreateTime);

        return ticketMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public IPage<Ticket> listAllTickets(Long tenantId, String status, Long assigneeId, int page, int size) {
        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "租户ID不能为空");
        }

        Page<Ticket> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Ticket> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Ticket::getTenantId, tenantId);

        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq(Ticket::getStatus, status);
        }

        if (assigneeId != null) {
            wrapper.eq(Ticket::getAssignee, assigneeId);
        }

        wrapper.orderByDesc(Ticket::getCreateTime);

        return ticketMapper.selectPage(pageParam, wrapper);
    }

    /**
     * 将List<Map<String,Object>>转换为Map<String, Long>
     * */
    private Map<String, Long> toMap(List<Map<String,Object>> rows, String key) {
        Map<String, Long> m = new HashMap<>();
        for (Map<String,Object> row : rows) {
            String k = String.valueOf(row.get(key));
            Long v = ((Number)row.get("cnt")).longValue();
            m.put(k, v);
        }
        return m;
    }
}
