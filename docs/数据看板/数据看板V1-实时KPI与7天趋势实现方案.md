# 数据看板 V1 实现方案（实时 KPI + 7 天趋势）

## 1. 目标与范围

本版本只做一件事：把你现在 `AdminDashboardController` 的占位数据变成可用数据，并在前端展示成可读图表。

- 实时 KPI（30 秒自动刷新）
  - 今日会话数（`chat_session`）
  - 今日转人工数（`handoff_request`）
  - 待处理工单数（`ticket`：`PENDING/PROCESSING`）
  - 在线客服数（Redis：`AgentStatusService#getOnlineAgents`）
- 7 天趋势（按天）
  - 会话数趋势
  - 转人工趋势
- 暂不纳入（放 V2）
  - 复杂漏斗分析、同比/环比、导出、分钟级实时流

---

## 2. 现状问题（你项目里的真实情况）

当前文件：`src/main/java/com/ityfz/yulu/admin/controller/AdminDashboardController.java`

- `/api/admin/dashboard/stats` 仍是硬编码 0。
- 前端 `frontend/src/pages/admin/AdminDashboardPage.tsx` 是占位页。
- 你的项目已有可复用能力：
  - 多租户：`SecurityUtil.currentTenantId()`
  - Redis 客服在线：`com.ityfz.yulu.user.service.AgentStatusService`

所以 V1 最优做法是：**保留原 controller 路由，新增 service + mapper 聚合查询**。

---

## 3. 接口设计（V1）

### 3.1 路径

`GET /api/admin/dashboard/overview`

### 3.2 返回结构

```json
{
  "code": "200",
  "message": "OK",
  "data": {
    "kpi": {
      "todaySessionCount": 128,
      "todayHandoffCount": 36,
      "pendingTicketCount": 19,
      "onlineAgentCount": 7,
      "refreshTime": "2026-02-25 16:10:00"
    },
    "trend": [
      { "date": "2026-02-19", "sessionCount": 91, "handoffCount": 20 },
      { "date": "2026-02-20", "sessionCount": 103, "handoffCount": 25 }
    ]
  }
}
```

---

## 4. 后端完整代码

> 下面代码按你当前包结构组织，可直接落地。

### 4.1 VO 定义

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
    /** yyyy-MM-dd */
    private String date;
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

### 4.2 Mapper（聚合查询）

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
                COUNT(1) AS session_count
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
                COUNT(1) AS handoff_count
            FROM handoff_request
            WHERE tenant_id = #{tenantId}
              AND create_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)
            GROUP BY DATE(create_time)
            ORDER BY DATE(create_time)
            """)
    List<DashboardTrendPointVO> queryHandoffTrend7d(@Param("tenantId") Long tenantId);
}
```

> 如果你的 MyBatis 全局没有开启下划线转驼峰（`session_count -> sessionCount`），改成 XML `resultMap`，或把 SQL 别名改成 `sessionCount` / `handoffCount`。

---

### 4.3 Service

#### `src/main/java/com/ityfz/yulu/admin/service/AdminDashboardService.java`

```java
package com.ityfz.yulu.admin.service;

import com.ityfz.yulu.admin.vo.DashboardOverviewVO;

public interface AdminDashboardService {
    DashboardOverviewVO getOverview(Long tenantId);
}
```

#### `src/main/java/com/ityfz/yulu/admin/service/Impl/AdminDashboardServiceImpl.java`

```java
package com.ityfz.yulu.admin.service.Impl;

import com.ityfz.yulu.admin.mapper.AdminDashboardMapper;
import com.ityfz.yulu.admin.service.AdminDashboardService;
import com.ityfz.yulu.admin.vo.DashboardKpiVO;
import com.ityfz.yulu.admin.vo.DashboardOverviewVO;
import com.ityfz.yulu.admin.vo.DashboardTrendPointVO;
import com.ityfz.yulu.user.service.AgentStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final AdminDashboardMapper dashboardMapper;
    private final AgentStatusService agentStatusService;

    @Override
    public DashboardOverviewVO getOverview(Long tenantId) {
        DashboardOverviewVO overview = new DashboardOverviewVO();
        overview.setKpi(buildKpi(tenantId));
        overview.setTrend(buildTrend(tenantId));
        return overview;
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
        List<DashboardTrendPointVO> sessionRows = dashboardMapper.querySessionTrend7d(tenantId);
        List<DashboardTrendPointVO> handoffRows = dashboardMapper.queryHandoffTrend7d(tenantId);

        Map<String, DashboardTrendPointVO> merged = new LinkedHashMap<>();

        // 先填满最近 7 天，保证前端图表永远 7 个点
        LocalDate start = LocalDate.now().minusDays(6);
        for (int i = 0; i < 7; i++) {
            String d = start.plusDays(i).toString();
            DashboardTrendPointVO p = new DashboardTrendPointVO();
            p.setDate(d);
            p.setSessionCount(0L);
            p.setHandoffCount(0L);
            merged.put(d, p);
        }

        if (sessionRows != null) {
            for (DashboardTrendPointVO row : sessionRows) {
                DashboardTrendPointVO p = merged.get(row.getDate());
                if (p != null) {
                    p.setSessionCount(defaultZero(row.getSessionCount()));
                }
            }
        }

        if (handoffRows != null) {
            for (DashboardTrendPointVO row : handoffRows) {
                DashboardTrendPointVO p = merged.get(row.getDate());
                if (p != null) {
                    p.setHandoffCount(defaultZero(row.getHandoffCount()));
                }
            }
        }

        return new ArrayList<>(merged.values());
    }

    private long defaultZero(Long v) {
        return v == null ? 0L : v;
    }
}
```

---

### 4.4 Controller 改造

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
@Tag(name = "B端-数据看板（Admin/Dashboard）", description = "实时 KPI + 趋势数据")
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    @GetMapping("/overview")
    @Operation(summary = "数据看板总览", description = "返回实时 KPI 和最近 7 天趋势")
    public ApiResponse<DashboardOverviewVO> overview() {
        Long tenantId = SecurityUtil.currentTenantId();
        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "缺少租户信息，请先登录");
        }
        return ApiResponse.success("OK", dashboardService.getOverview(tenantId));
    }
}
```

> 你也可以兼容旧路由 `/stats`，让前端平滑迁移：在 controller 增加一个 `@GetMapping("/stats")` 代理到同一个 service。

---

## 5. 前端完整代码

### 5.1 安装图表依赖

```bash
cd frontend
npm i echarts echarts-for-react
```

---

### 5.2 API 封装

#### `frontend/src/api/dashboard.ts`

```ts
import http from './axios';
import type { ApiResponse } from './types';

export interface DashboardKpi {
  todaySessionCount: number;
  todayHandoffCount: number;
  pendingTicketCount: number;
  onlineAgentCount: number;
  refreshTime: string;
}

export interface DashboardTrendPoint {
  date: string;
  sessionCount: number;
  handoffCount: number;
}

export interface DashboardOverview {
  kpi: DashboardKpi;
  trend: DashboardTrendPoint[];
}

export const dashboardApi = {
  overview() {
    return http.get<ApiResponse<DashboardOverview>>('/admin/dashboard/overview');
  }
};
```

---

### 5.3 页面实现（KPI + 7 天趋势 + 30 秒刷新）

#### `frontend/src/pages/admin/AdminDashboardPage.tsx`

```tsx
import { useEffect, useMemo, useRef, useState } from 'react';
import { Card, Col, Row, Statistic, Spin, Empty } from 'antd';
import ReactECharts from 'echarts-for-react';
import { dashboardApi, type DashboardOverview } from '../../api/dashboard';

const REFRESH_MS = 30_000;

export default function AdminDashboardPage() {
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<DashboardOverview | null>(null);
  const timerRef = useRef<number | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      const res = await dashboardApi.overview();
      setData(res.data.data);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    timerRef.current = window.setInterval(load, REFRESH_MS);
    return () => {
      if (timerRef.current) {
        window.clearInterval(timerRef.current);
      }
    };
  }, []);

  const trendOption = useMemo(() => {
    const trend = data?.trend ?? [];
    const x = trend.map((i) => i.date.slice(5));
    const session = trend.map((i) => i.sessionCount);
    const handoff = trend.map((i) => i.handoffCount);

    return {
      tooltip: { trigger: 'axis' },
      legend: { data: ['会话数', '转人工数'] },
      grid: { left: 24, right: 24, top: 40, bottom: 24, containLabel: true },
      xAxis: { type: 'category', data: x },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        { name: '会话数', type: 'line', smooth: true, data: session },
        { name: '转人工数', type: 'line', smooth: true, data: handoff }
      ]
    };
  }, [data]);

  return (
    <Spin spinning={loading}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <Row gutter={16}>
          <Col span={6}>
            <Card>
              <Statistic title="今日会话" value={data?.kpi.todaySessionCount ?? 0} />
            </Card>
          </Col>
          <Col span={6}>
            <Card>
              <Statistic title="今日转人工" value={data?.kpi.todayHandoffCount ?? 0} />
            </Card>
          </Col>
          <Col span={6}>
            <Card>
              <Statistic title="待处理工单" value={data?.kpi.pendingTicketCount ?? 0} />
            </Card>
          </Col>
          <Col span={6}>
            <Card>
              <Statistic title="在线客服" value={data?.kpi.onlineAgentCount ?? 0} />
            </Card>
          </Col>
        </Row>

        <Card title={`近 7 天趋势（更新时间：${data?.kpi.refreshTime ?? '-'}）`}>
          {data?.trend?.length ? (
            <ReactECharts option={trendOption} style={{ height: 360 }} notMerge lazyUpdate />
          ) : (
            <Empty description="暂无趋势数据" />
          )}
        </Card>
      </div>
    </Spin>
  );
}
```

---

## 6. 实施步骤（按这个顺序做）

1. 新增 VO：`DashboardKpiVO`、`DashboardTrendPointVO`、`DashboardOverviewVO`。
2. 新增 `AdminDashboardMapper`（先把 SQL 跑通）。
3. 新增 `AdminDashboardService` + `AdminDashboardServiceImpl`（做合并补零逻辑）。
4. 改造 `AdminDashboardController`，增加 `/overview`。
5. 前端安装 `echarts` + `echarts-for-react`。
6. 新增 `frontend/src/api/dashboard.ts`，替换 `AdminDashboardPage.tsx`。
7. 联调验证（见下一节）。

---

## 7. 联调与测试清单

### 7.1 后端接口自测

- 登录管理员，拿 token。
- 调：`GET /api/admin/dashboard/overview`
- 校验点：
  - code = 200
  - `kpi` 四个字段非空
  - `trend` 固定 7 个点（即使某天无数据也是 0）

### 7.2 SQL 对账

```sql
-- 今日会话
SELECT COUNT(1) FROM chat_session
WHERE tenant_id = 1 AND create_time >= CURDATE();

-- 今日转人工
SELECT COUNT(1) FROM handoff_request
WHERE tenant_id = 1 AND create_time >= CURDATE();

-- 待处理工单
SELECT COUNT(1) FROM ticket
WHERE tenant_id = 1 AND status IN ('PENDING','PROCESSING');
```

页面 KPI 必须与 SQL 对得上。

### 7.3 前端行为

- 打开 `/admin/dashboard` 首次加载成功。
- 不刷新页面，30 秒后自动刷新一次。
- 最近 7 天趋势折线正常展示。

---

## 8. 你下一步可做的 V2（可选）

- 增加“环比昨日”字段（`today vs yesterday`）。
- 支持时间范围筛选（7/30/90 天）。
- 增加导出：CSV/Excel。
- 用 WebSocket 推 KPI（替代轮询）。

---

## 9. FAQ 反馈功能当前是否已实现

已实现（后端）。

- 控制器：`src/main/java/com/ityfz/yulu/faq/controller/CustomerFAQController.java`
  - `POST /api/customer/faq/feedback`
- 服务：`src/main/java/com/ityfz/yulu/faq/service/Impl/FaqCustomerServiceImpl.java`
  - `feedback(...)` 会写入/更新 `faq_feedback`，并更新 FAQ 的点赞/点踩计数。

如果你要，我下一步可以给你一版“用户端 FAQ 页面接入 feedback 按钮并防重复提交”的前端代码。
