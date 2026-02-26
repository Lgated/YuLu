# 数据看板优化方案（定时聚合 + Redis缓存 + 接口改造）

> 目标：保留前端“手动刷新 + 30s 自动刷新”的交互，但让接口主要读 Redis，减少每次请求直接打 DB。

## 1. 方案概览

当前是前端轮询 `/api/admin/dashboard/overview`，后端实时查库。优化后改为：

1. 后端定时任务按租户聚合看板数据（KPI + 7天趋势）
2. 聚合结果写入 Redis
3. 接口优先读 Redis，缓存 miss 时兜底查库并回填缓存

这样前端保持不变，后端压力显著降低。

---

## 2. 数据结构与缓存键

建议 Redis Key：

- `dashboard:overview:{tenantId}`  -> JSON（DashboardOverviewVO）

TTL 建议：120 秒（比调度间隔稍长，防止偶发调度失败导致缓存空窗）。

---

## 3. 后端代码落地

以下代码按你现有包名 `com.ityfz.yulu` 给出。

### 3.1 复用 VO（你已有可跳过）

#### `src/main/java/com/ityfz/yulu/admin/vo/DashboardKpiVO.java`

```java
package com.ityfz.yulu.admin.vo;

import lombok.Data;

@Data
public class DashboardKpiVO {
    private Long todaySessionCount;
    private Long todayHandoffCount;
    private Long pendingTicketCount;
    private Long onlineAgentCount;
    private String refreshTime;
}
```

#### `src/main/java/com/ityfz/yulu/admin/vo/DashboardTrendPointVO.java`

```java
package com.ityfz.yulu.admin.vo;

import lombok.Data;

@Data
public class DashboardTrendPointVO {
    private String date; // yyyy-MM-dd
    private Long sessionCount;
    private Long handoffCount;
}
```

#### `src/main/java/com/ityfz/yulu/admin/vo/DashboardOverviewVO.java`

```java
package com.ityfz.yulu.admin.vo;

import lombok.Data;

import java.util.List;

@Data
public class DashboardOverviewVO {
    private DashboardKpiVO kpi;
    private List<DashboardTrendPointVO> trend;
}
```

---

### 3.2 Mapper（查库聚合）

#### `src/main/java/com/ityfz/yulu/admin/mapper/AdminDashboardMapper.java`

```java
package com.ityfz.yulu.admin.mapper;

import com.ityfz.yulu.admin.vo.DashboardTrendPointVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AdminDashboardMapper {

    @Select("""
            SELECT COUNT(1)
            FROM chat_session
            WHERE tenant_id = #{tenantId}
              AND create_time >= CURDATE()
            """)
    Long countTodaySession(@Param("tenantId") Long tenantId);

    @Select("""
            SELECT COUNT(1)
            FROM handoff_request
            WHERE tenant_id = #{tenantId}
              AND create_time >= CURDATE()
            """)
    Long countTodayHandoff(@Param("tenantId") Long tenantId);

    @Select("""
            SELECT COUNT(1)
            FROM ticket
            WHERE tenant_id = #{tenantId}
              AND status IN ('PENDING','PROCESSING')
            """)
    Long countPendingTicket(@Param("tenantId") Long tenantId);

    @Select("""
            SELECT
                DATE(create_time) AS date,
                COUNT(1) AS sessionCount
            FROM chat_session
            WHERE tenant_id = #{tenantId}
              AND create_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)
            GROUP BY DATE(create_time)
            ORDER BY DATE(create_time)
            """)
    List<DashboardTrendPointVO> querySessionTrend7d(@Param("tenantId") Long tenantId);

    @Select("""
            SELECT
                DATE(create_time) AS date,
                COUNT(1) AS handoffCount
            FROM handoff_request
            WHERE tenant_id = #{tenantId}
              AND create_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)
            GROUP BY DATE(create_time)
            ORDER BY DATE(create_time)
            """)
    List<DashboardTrendPointVO> queryHandoffTrend7d(@Param("tenantId") Long tenantId);
}
```

---

### 3.3 新增 Service（DB计算 + Redis缓存）

#### `src/main/java/com/ityfz/yulu/admin/service/AdminDashboardService.java`

```java
package com.ityfz.yulu.admin.service;

import com.ityfz.yulu.admin.vo.DashboardOverviewVO;

public interface AdminDashboardService {

    DashboardOverviewVO getOverview(Long tenantId);

    DashboardOverviewVO buildOverviewFromDb(Long tenantId);

    void refreshCacheForTenant(Long tenantId);
}
```

#### `src/main/java/com/ityfz/yulu/admin/service/impl/AdminDashboardServiceImpl.java`

```java
package com.ityfz.yulu.admin.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ityfz.yulu.admin.mapper.AdminDashboardMapper;
import com.ityfz.yulu.admin.service.AdminDashboardService;
import com.ityfz.yulu.admin.vo.DashboardKpiVO;
import com.ityfz.yulu.admin.vo.DashboardOverviewVO;
import com.ityfz.yulu.admin.vo.DashboardTrendPointVO;
import com.ityfz.yulu.user.service.AgentStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final String KEY_PREFIX = "dashboard:overview:";
    private static final long CACHE_TTL_SECONDS = 120;

    private final AdminDashboardMapper dashboardMapper;
    private final AgentStatusService agentStatusService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public DashboardOverviewVO getOverview(Long tenantId) {
        String key = KEY_PREFIX + tenantId;
        String cache = stringRedisTemplate.opsForValue().get(key);
        if (cache != null && !cache.isBlank()) {
            try {
                return objectMapper.readValue(cache, DashboardOverviewVO.class);
            } catch (Exception e) {
                log.warn("[Dashboard] cache parse fail, tenantId={}", tenantId, e);
            }
        }

        DashboardOverviewVO latest = buildOverviewFromDb(tenantId);
        writeCache(key, latest);
        return latest;
    }

    @Override
    public DashboardOverviewVO buildOverviewFromDb(Long tenantId) {
        DashboardOverviewVO vo = new DashboardOverviewVO();
        vo.setKpi(buildKpi(tenantId));
        vo.setTrend(buildTrend(tenantId));
        return vo;
    }

    @Override
    public void refreshCacheForTenant(Long tenantId) {
        String key = KEY_PREFIX + tenantId;
        DashboardOverviewVO latest = buildOverviewFromDb(tenantId);
        writeCache(key, latest);
    }

    private DashboardKpiVO buildKpi(Long tenantId) {
        DashboardKpiVO kpi = new DashboardKpiVO();
        kpi.setTodaySessionCount(defaultZero(dashboardMapper.countTodaySession(tenantId)));
        kpi.setTodayHandoffCount(defaultZero(dashboardMapper.countTodayHandoff(tenantId)));
        kpi.setPendingTicketCount(defaultZero(dashboardMapper.countPendingTicket(tenantId)));

        List<Long> onlineAgents = agentStatusService.getOnlineAgents(tenantId);
        kpi.setOnlineAgentCount((long) (onlineAgents == null ? 0 : onlineAgents.size()));

        kpi.setRefreshTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return kpi;
    }

    private List<DashboardTrendPointVO> buildTrend(Long tenantId) {
        List<DashboardTrendPointVO> sessions = dashboardMapper.querySessionTrend7d(tenantId);
        List<DashboardTrendPointVO> handoffs = dashboardMapper.queryHandoffTrend7d(tenantId);

        Map<String, DashboardTrendPointVO> merged = new LinkedHashMap<>();

        LocalDate start = LocalDate.now().minusDays(6);
        for (int i = 0; i < 7; i++) {
            String d = start.plusDays(i).toString();
            DashboardTrendPointVO p = new DashboardTrendPointVO();
            p.setDate(d);
            p.setSessionCount(0L);
            p.setHandoffCount(0L);
            merged.put(d, p);
        }

        if (sessions != null) {
            for (DashboardTrendPointVO row : sessions) {
                DashboardTrendPointVO p = merged.get(row.getDate());
                if (p != null) p.setSessionCount(defaultZero(row.getSessionCount()));
            }
        }

        if (handoffs != null) {
            for (DashboardTrendPointVO row : handoffs) {
                DashboardTrendPointVO p = merged.get(row.getDate());
                if (p != null) p.setHandoffCount(defaultZero(row.getHandoffCount()));
            }
        }

        return new ArrayList<>(merged.values());
    }

    private long defaultZero(Long value) {
        return value == null ? 0L : value;
    }

    private void writeCache(String key, DashboardOverviewVO value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            stringRedisTemplate.opsForValue().set(key, json, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.error("[Dashboard] cache write fail, key={}", key, e);
        }
    }
}
```

---

### 3.4 新增租户查询 Mapper（定时任务需要）

#### `src/main/java/com/ityfz/yulu/admin/mapper/AdminTenantMapper.java`

```java
package com.ityfz.yulu.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AdminTenantMapper {

    @Select("SELECT id FROM tenant WHERE status = 1")
    List<Long> queryActiveTenantIds();
}
```

> 如果你的 `tenant` 表没有 `status`，改成 `SELECT id FROM tenant`。

---

### 3.5 新增定时任务

#### `src/main/java/com/ityfz/yulu/admin/task/DashboardCacheRefreshTask.java`

```java
package com.ityfz.yulu.admin.task;

import com.ityfz.yulu.admin.mapper.AdminTenantMapper;
import com.ityfz.yulu.admin.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DashboardCacheRefreshTask {

    private final AdminTenantMapper tenantMapper;
    private final AdminDashboardService dashboardService;

    /** 每30秒刷新一次所有租户缓存 */
    @Scheduled(fixedDelay = 30_000, initialDelay = 10_000)
    public void refreshAllTenantDashboardCache() {
        List<Long> tenantIds = tenantMapper.queryActiveTenantIds();
        if (tenantIds == null || tenantIds.isEmpty()) {
            return;
        }

        for (Long tenantId : tenantIds) {
            try {
                dashboardService.refreshCacheForTenant(tenantId);
            } catch (Exception e) {
                log.error("[Dashboard] refresh cache fail, tenantId={}", tenantId, e);
            }
        }
    }
}
```

---

### 3.6 开启定时任务

#### `src/main/java/com/ityfz/yulu/YuLuApplication.java`

```java
@SpringBootApplication
@EnableScheduling
public class YuLuApplication {
    public static void main(String[] args) {
        SpringApplication.run(YuLuApplication.class, args);
    }
}
```

如果你已有 `@EnableScheduling`，这步跳过。

---

### 3.7 Controller 接口改造（仅调用 service）

#### `src/main/java/com/ityfz/yulu/admin/controller/AdminDashboardController.java`

```java
package com.ityfz.yulu.admin.controller;

import com.ityfz.yulu.admin.service.AdminDashboardService;
import com.ityfz.yulu.admin.vo.DashboardOverviewVO;
import com.ityfz.yulu.common.annotation.RequireRole;
import com.ityfz.yulu.common.enums.ErrorCodes;
import com.ityfz.yulu.common.exception.BizException;
import com.ityfz.yulu.common.model.ApiResponse;
import com.ityfz.yulu.common.security.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@RequireRole({"ADMIN", "AGENT"})
@Tag(name = "B端-数据看板（Admin/Dashboard）", description = "实时KPI与趋势")
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    @GetMapping("/overview")
    @Operation(summary = "看板总览", description = "优先读Redis缓存，缓存失效时自动回源DB")
    public ApiResponse<DashboardOverviewVO> overview() {
        Long tenantId = SecurityUtil.currentTenantId();
        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "缺少租户信息，请先登录");
        }
        return ApiResponse.success("OK", dashboardService.getOverview(tenantId));
    }
}
```

---

## 4. 前端是否需要改

不需要大改，保持你现在的轮询 + 手动刷新即可。

- 轮询请求仍是 `/api/admin/dashboard/overview`
- 只是后端现在大多数情况下是 Redis 命中，接口响应更稳定

---

## 5. 实施步骤（建议顺序）

1. 加 `AdminDashboardMapper` 查询 SQL。
2. 加 `AdminDashboardServiceImpl`（先跑通 DB + cache read/write）。
3. 改 `AdminDashboardController` 调 service。
4. 加 `AdminTenantMapper` + `DashboardCacheRefreshTask`。
5. 打开 `@EnableScheduling`。
6. 联调验证缓存命中与失效回源。

---

## 6. 验证与排查

### 6.1 手工验证

1. 先请求 `/api/admin/dashboard/overview` 一次。
2. 在 Redis 查 key：`dashboard:overview:{tenantId}` 是否存在。
3. 再次请求，观察日志应无聚合 SQL 大量执行。

### 6.2 常见问题

1. **缓存读不到**
   - 检查 `StringRedisTemplate` 是否可用
   - 检查 key 前缀和 tenantId

2. **趋势字段都是0**
   - 检查 SQL 时间范围 `DATE_SUB(CURDATE(), INTERVAL 6 DAY)`
   - 检查字段别名是否映射到 `sessionCount/handoffCount`

3. **定时任务不触发**
   - 检查是否加了 `@EnableScheduling`
   - 检查 task 类是否被 Spring 扫描

---

## 7. 进一步增强（可选）

1. 加本地锁避免并发刷新同一租户缓存。
2. 高租户量时把“全量刷新”改成“分片刷新”。
3. 增加 `/api/admin/dashboard/refresh`（管理员手动触发后端重算）。
4. 监控指标：缓存命中率、聚合耗时、任务成功率。

---

## 8. 结论

这个方案比“每次轮询都查库”更适合你当前阶段：

- 保持前端简单（无需上 WS）
- 后端压力小，稳定性更高
- 可平滑升级到 WebSocket（后续只需把缓存结果推送出去）
