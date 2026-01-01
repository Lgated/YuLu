package com.ityfz.yulu.ticket.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ityfz.yulu.ticket.entity.Ticket;
import com.ityfz.yulu.ticket.enums.TicketStatus;
import com.ityfz.yulu.ticket.mapper.TicketMapper;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketJobHandler {
    private final TicketMapper ticketMapper;

    /**
     * 工单超时自动关闭任务
     * 功能：查询超过7天未处理的工单（状态为PENDING或PROCESSING），自动关闭
     * Cron表达式：0 0 2 * * ? （每天凌晨2点执行）
     */
    @XxlJob("ticketAutoClose")
    //把方法注册为 XXL-JOB 执行器中的一个任务，调度中心里新增任务时填的 JobHandler 名称就是这个字符串
    public void ticketAutoClose() {
        // 记录任务开始时间
        long startTime = System.currentTimeMillis();
        XxlJobHelper.log("[TicketJob] ========== 开始执行工单超时自动关闭任务 ==========");
        try {
            // 1. 计算截止时间：7天前
            LocalDateTime deadline = LocalDateTime.now().minusDays(7);
            //把日志实时回写到 XXL-JOB 控制台
            //与 log.info(...) 区别：log.info 只落在本地文件，XxlJobHelper.log 能在 Web 页面看到每条记录。
            XxlJobHelper.log("[TicketJob] 截止时间：{}（7天前）", deadline);

            // 2. 查询超过7天未处理的工单
            // 条件：状态为PENDING（待处理）或PROCESSING（处理中），且创建时间早于截止时间
            List<Ticket> expiredTickets = ticketMapper.selectList(
                    new LambdaQueryWrapper<Ticket>()
                            .in(Ticket::getStatus, TicketStatus.PENDING.getCode(), TicketStatus.PROCESSING.getCode())
                            .lt(Ticket::getCreateTime, deadline)
            );

            if (expiredTickets == null || expiredTickets.isEmpty()) {
                XxlJobHelper.log("[TicketJob] 没有需要关闭的工单");
                log.info("[TicketJob] 没有需要关闭的工单");
                return;
            }

            XxlJobHelper.log("[TicketJob] 查询到{}个超时工单，开始批量关闭", expiredTickets.size());
            log.info("[TicketJob] 查询到{}个超时工单，开始批量关闭", expiredTickets.size());

            // 3. 批量更新状态为CLOSED
            int successCount = 0;
            int failCount = 0;
            LocalDateTime now = LocalDateTime.now();

            for (Ticket ticket : expiredTickets) {
                try {
                    // 更新工单状态
                    ticket.setStatus(TicketStatus.CLOSED.getCode());
                    ticket.setUpdateTime(now);

                    int updateResult = ticketMapper.updateById(ticket);

                    if (updateResult > 0) {
                        successCount++;
                        XxlJobHelper.log("[TicketJob] 工单已关闭: ticketId={}, title={}, 创建时间={}",
                                ticket.getId(), ticket.getTitle(), ticket.getCreateTime());
                        log.info("[TicketJob] 工单已关闭: ticketId={}, title={}", ticket.getId(), ticket.getTitle());
                    } else {
                        failCount++;
                        XxlJobHelper.log("[TicketJob] 工单关闭失败: ticketId={}, 更新结果={}",
                                ticket.getId(), updateResult);
                        log.warn("[TicketJob] 工单关闭失败: ticketId={}", ticket.getId());
                    }
                } catch (Exception e) {
                    failCount++;
                    XxlJobHelper.log("[TicketJob] 工单关闭异常: ticketId={}, error={}",
                            ticket.getId(), e.getMessage());
                    log.error("[TicketJob] 工单关闭异常: ticketId={}", ticket.getId(), e);
                }
            }

            // 4. 记录任务执行结果
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            XxlJobHelper.log("[TicketJob] ========== 任务执行完成 ==========");
            XxlJobHelper.log("[TicketJob] 总工单数: {}", expiredTickets.size());
            XxlJobHelper.log("[TicketJob] 成功关闭: {}", successCount);
            XxlJobHelper.log("[TicketJob] 失败数量: {}", failCount);
            XxlJobHelper.log("[TicketJob] 执行耗时: {}ms", duration);

            log.info("[TicketJob] 任务执行完成: 总数={}, 成功={}, 失败={}, 耗时={}ms",
                    expiredTickets.size(), successCount, failCount, duration);

        } catch (Exception e) {
            XxlJobHelper.log("[TicketJob] 任务执行失败: {}", e.getMessage());
            log.error("[TicketJob] 任务执行失败", e);
            // 抛出异常，XXL-Job会记录为失败任务
            throw new RuntimeException("工单超时自动关闭任务执行失败", e);
        }
    }
}
