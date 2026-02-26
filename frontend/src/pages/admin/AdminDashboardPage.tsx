import { useEffect, useMemo, useRef, useState } from 'react';
import { Button, Card, Col, Empty, Row, Segmented, Space, Spin, Statistic, Typography } from 'antd';
import ReactECharts from 'echarts-for-react';
import { dashboardApi, type DashboardOverview } from '../../api/dashboard';

const REFRESH_MS = 30_000;
const RANGE_OPTIONS = [
  { label: '近7天', value: 7 },
  { label: '近30天', value: 30 },
  { label: '近90天', value: 90 }
];

function formatNow() {
  const now = new Date();
  const p = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${p(now.getMonth() + 1)}-${p(now.getDate())} ${p(now.getHours())}:${p(now.getMinutes())}:${p(now.getSeconds())}`;
}

export default function AdminDashboardPage() {
  const [loading, setLoading] = useState(false);
  const [manualRefreshing, setManualRefreshing] = useState(false);
  const [overview, setOverview] = useState<DashboardOverview | null>(null);
  const [lastRefreshTime, setLastRefreshTime] = useState<string>('-');
  const [rangeDays, setRangeDays] = useState<number>(7);
  const timerRef = useRef<number | null>(null);

  const loadData = async (manual = false, days = rangeDays) => {
    if (manual) {
      setManualRefreshing(true);
    } else {
      setLoading(true);
    }

    try {
      const data = await dashboardApi.overview(days);
      setOverview(data);
      setLastRefreshTime(data?.kpi?.refreshTime || formatNow());
    } finally {
      setLoading(false);
      setManualRefreshing(false);
    }
  };

  useEffect(() => {
    loadData(false, rangeDays);

    timerRef.current = window.setInterval(() => {
      loadData(false, rangeDays);
    }, REFRESH_MS);

    return () => {
      if (timerRef.current) {
        window.clearInterval(timerRef.current);
      }
    };
  }, [rangeDays]);

  const trendOption = useMemo(() => {
    const trend = overview?.trend || [];
    const xAxisData = trend.map((item) => item.date?.slice(5) || item.date);
    const sessionData = trend.map((item) => item.sessionCount || 0);
    const handoffData = trend.map((item) => item.handoffCount || 0);

    return {
      tooltip: { trigger: 'axis' },
      legend: { data: ['会话数', '转人工数'], bottom: 0 },
      grid: { left: 20, right: 20, top: 48, bottom: 56, containLabel: true },
      xAxis: { type: 'category', boundaryGap: false, data: xAxisData },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        {
          name: '会话数',
          type: 'line',
          smooth: true,
          data: sessionData,
          lineStyle: { width: 3 }
        },
        {
          name: '转人工数',
          type: 'line',
          smooth: true,
          data: handoffData,
          lineStyle: { width: 3 }
        }
      ]
    };
  }, [overview]);

  return (
    <Spin spinning={loading}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <Row gutter={16}>
          <Col span={6}>
            <Card>
              <Statistic title="今日会话数" value={overview?.kpi.todaySessionCount ?? 0} />
            </Card>
          </Col>
          <Col span={6}>
            <Card>
              <Statistic title="今日转人工数" value={overview?.kpi.todayHandoffCount ?? 0} />
            </Card>
          </Col>
          <Col span={6}>
            <Card>
              <Statistic title="待处理工单数" value={overview?.kpi.pendingTicketCount ?? 0} />
            </Card>
          </Col>
          <Col span={6}>
            <Card>
              <Statistic title="在线客服数" value={overview?.kpi.onlineAgentCount ?? 0} />
            </Card>
          </Col>
        </Row>

        <Card
          title={`${rangeDays}天趋势`}
          extra={
            <Space size={12}>
              <Segmented
                options={RANGE_OPTIONS}
                value={rangeDays}
                onChange={(value) => setRangeDays(Number(value))}
              />
              <Typography.Text type="secondary">最近刷新：{lastRefreshTime}</Typography.Text>
              <Button size="small" loading={manualRefreshing} onClick={() => loadData(true, rangeDays)}>
                手动刷新
              </Button>
            </Space>
          }
        >
          {overview?.trend?.length ? (
            <ReactECharts option={trendOption} style={{ height: 360 }} notMerge lazyUpdate />
          ) : (
            <Empty description="暂无趋势数据" />
          )}
        </Card>
      </div>
    </Spin>
  );
}


