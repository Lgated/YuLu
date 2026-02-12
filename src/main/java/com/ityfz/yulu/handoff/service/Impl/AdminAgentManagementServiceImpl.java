package com.ityfz.yulu.handoff.service.Impl;

import com.ityfz.yulu.common.enums.Roles;
import com.ityfz.yulu.handoff.dto.WebSocketMessage;
import com.ityfz.yulu.handoff.service.AdminAgentManagementService;
import com.ityfz.yulu.handoff.vo.AgentMonitorVO;
import com.ityfz.yulu.handoff.websocket.AgentWebSocketHandler;
import com.ityfz.yulu.ticket.service.NotificationService;
import com.ityfz.yulu.user.entity.User;
import com.ityfz.yulu.user.service.AgentStatusService;
import com.ityfz.yulu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAgentManagementServiceImpl implements AdminAgentManagementService {

    private final AgentStatusService agentStatusService;
    private final AgentWebSocketHandler agentHandler;
    private final UserService userService;
    private final NotificationService notificationService;

    @Override
    public List<AgentMonitorVO> getAgentMonitorList(Long tenantId) {
        // 1) 查出本租户所有客服
        List<User> agents = userService.listByTenantIdAndRole(tenantId, Roles.AGENT);

        if (agents == null || agents.isEmpty()) {
            return Collections.emptyList();
        }

        // 2) 逐个读取 Redis 状态
        return agents.stream().map(agent -> {
            Map<String, Object> statusMap = agentStatusService.getAgentStatus(tenantId, agent.getId());

            String status = toStr(statusMap.get("status"), "OFFLINE");
            Integer currentSessions = toInt(statusMap.get("current_sessions"), 0);
            Integer maxSessions = toInt(statusMap.get("max_sessions"), 0);

            // last_active_time 在 Redis 里是字符串
            LocalDateTime lastActiveTime = toDateTime(statusMap.get("last_active_time"));

            return AgentMonitorVO.builder()
                    .agentId(agent.getId())
                    .agentName(agent.getNickName() != null ? agent.getNickName() : agent.getUsername())
                    .status(status)
                    .currentSessions(currentSessions)
                    .maxSessions(maxSessions)
                    .lastActiveTime(lastActiveTime)
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public void forceUpdateAgentStatus(Long tenantId, Long agentId, String newStatus) {
        if (newStatus == null) return;
        String status = newStatus.toUpperCase();

        if ("ONLINE".equals(status)) {
            agentStatusService.setOnline(tenantId, agentId);
        } else if ("OFFLINE".equals(status)) {
            agentStatusService.setOffline(tenantId, agentId);
        } else if ("AWAY".equals(status)) {
            agentStatusService.setAway(tenantId, agentId);
        } else {
            return; // 或抛 BizException
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("status", status);

        WebSocketMessage msg = WebSocketMessage.builder()
                .type("ADMIN_FORCE_STATUS")
                .payload(payload)
                .timestamp(LocalDateTime.now().toString())
                .build();

        agentHandler.sendToAgent(tenantId, agentId, msg);
    }

    @Override
    public void broadcastNotification(Long tenantId, Long senderUserId, String title, String content) {
        List<User> agents = userService.listByTenantIdAndRole(tenantId, Roles.AGENT);
        List<Long> agentIds = agents == null ? Collections.emptyList() : agents.stream()
                .map(User::getId)
                .collect(Collectors.toList());
        notificationService.notifyBroadcast(tenantId, agentIds, title, content);
        if (senderUserId != null) {
            notificationService.notifyBroadcast(tenantId, Collections.singletonList(senderUserId), title, content);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("content", content);

        WebSocketMessage msg = WebSocketMessage.builder()
                .type("ADMIN_NOTIFICATION")
                .payload(payload)
                .timestamp(LocalDateTime.now().toString())
                .build();

        agentHandler.broadcastToTenant(tenantId, msg);
    }

    // 安全转换方法，避免类型转换异常
    private String toStr(Object val, String def) {
        return val == null ? def : String.valueOf(val);
    }

    private Integer toInt(Object val, Integer def) {
        if (val == null) return def;
        if (val instanceof Number) return ((Number) val).intValue();
        try {
            return Integer.parseInt(val.toString());
        } catch (Exception e) {
            return def;
        }
    }

    private LocalDateTime toDateTime(Object val) {
        if (val == null) return null;
        try {
            return LocalDateTime.parse(val.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
