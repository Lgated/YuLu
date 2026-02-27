import { useEffect, useMemo, useRef, useState } from 'react';
import { Button, Card, Col, Empty, List, Row, Segmented, Space, Spin, Statistic, Tag, Typography } from 'antd';
import ReactECharts from 'echarts-for-react';
import {
  dashboardApi,
  type DashboardOverview,
  type IntentDistributionItem,
  type LowScoreItem,
  type RatingTrendPoint
} from '../../api/dashboard';

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

const INTENT_LABEL_MAP: Record<string, string> = {
  GENERAL: '通用咨询',
  REFUND: '退款',
  INVOICE: '发票',
  COMPLAINT: '投诉',
  LOGISTICS: '物流',
  PAYMENT: '支付',
  ACCOUNT: '账号',
  PRODUCT: '商品'
};

export default function AdminDashboardPage() {
  const [loading, setLoading] = useState(false);
  const [manualRefreshing, setManualRefreshing] = useState(false);
  const [overview, setOverview] = useState<DashboardOverview | null>(null);
  const [ratingTrend, setRatingTrend] = useState<RatingTrendPoint[]>([]);
  const [lowScoreList, setLowScoreList] = useState<LowScoreItem[]>([]);
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
      const [overviewData, trendData, lowScoreData] = await Promise.all([
        dashboardApi.overview(days),
        dashboardApi.ratingTrend(days).catch(() => []),
        dashboardApi.lowScoreTop(days, 5, 2).catch(() => [])
      ]);

      setOverview(overviewData);
      setRatingTrend(trendData);
      setLowScoreList(lowScoreData.length ? lowScoreData : (overviewData.lowScoreAlerts || []));
      setLastRefreshTime(overviewData?.kpi?.refreshTime || formatNow());
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

  const ratingTrendOption = useMemo(() => {
    const xAxisData = ratingTrend.map((item) => item.date?.slice(5) || item.date);
    const avgScoreData = ratingTrend.map((item) => item.avgScore || 0);
    const positiveRateData = ratingTrend.map((item) => item.positiveRate || 0);

    return {
      tooltip: { trigger: 'axis' },
      legend: { data: ['平均分', '好评率(%)'], bottom: 0 },
      grid: { left: 20, right: 20, top: 48, bottom: 56, containLabel: true },
      xAxis: { type: 'category', boundaryGap: false, data: xAxisData },
      yAxis: [
        { type: 'value', min: 0, max: 5, name: '平均分' },
        { type: 'value', min: 0, max: 100, name: '好评率(%)' }
      ],
      series: [
        {
          name: '平均分',
          type: 'line',
          smooth: true,
          yAxisIndex: 0,
          data: avgScoreData,
          lineStyle: { width: 3 }
        },
        {
          name: '好评率(%)',
          type: 'line',
          smooth: true,
          yAxisIndex: 1,
          data: positiveRateData,
          lineStyle: { width: 3 }
        }
      ]
    };
  }, [ratingTrend]);

  const intentOption = useMemo(() => {
    const source: IntentDistributionItem[] = overview?.intentDistribution || [];
    const chartData = source
      .filter((item) => item.count > 0)
      .map((item) => ({
        name: INTENT_LABEL_MAP[item.intent] || item.intent,
        value: item.count
      }));

    return {
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, type: 'scroll' },
      series: [
        {
          name: '意图分布',
          type: 'pie',
          radius: ['40%', '68%'],
          center: ['50%', '44%'],
          avoidLabelOverlap: true,
          data: chartData,
          label: { formatter: '{b}\n{d}%' }
        }
      ]
    };
  }, [overview?.intentDistribution]);

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

        <Row gutter={16}>
          <Col span={8}>
            <Card>
              <Statistic title="评分总数" value={overview?.kpi.ratingTotalCount ?? 0} />
            </Card>
          </Col>
          <Col span={8}>
            <Card>
              <Statistic title="平均满意度" value={overview?.kpi.ratingAvgScore ?? 0} precision={2} suffix="/5" />
            </Card>
          </Col>
          <Col span={8}>
            <Card>
              <Statistic title="好评率" value={overview?.kpi.ratingPositiveRate ?? 0} precision={2} suffix="%" />
            </Card>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col span={8}>
            <Card>
              <Statistic title="负向情绪消息数" value={overview?.kpi.negativeEmotionCount ?? 0} />
            </Card>
          </Col>
          <Col span={8}>
            <Card>
              <Statistic title="负向情绪率" value={overview?.kpi.negativeEmotionRate ?? 0} precision={2} suffix="%" />
            </Card>
          </Col>
          <Col span={8}>
            <Card title={`意图分布（近${rangeDays}天）`}>
              {overview?.intentDistribution?.length ? (
                <ReactECharts option={intentOption} style={{ height: 220 }} notMerge lazyUpdate />
              ) : (
                <Empty description="暂无意图数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              )}
            </Card>
          </Col>
        </Row>

        <Card
          title={`${rangeDays}天业务趋势`}
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
            <ReactECharts option={trendOption} style={{ height: 320 }} notMerge lazyUpdate />
          ) : (
            <Empty description="暂无业务趋势数据" />
          )}
        </Card>

        <Row gutter={16}>
          <Col span={16}>
            <Card title={`${rangeDays}天满意度趋势`}>
              {ratingTrend.length ? (
                <ReactECharts option={ratingTrendOption} style={{ height: 320 }} notMerge lazyUpdate />
              ) : (
                <Empty description="暂无满意度趋势数据" />
              )}
            </Card>
          </Col>
          <Col span={8}>
            <Card title={`低分评价Top5（近${rangeDays}天）`}>
              {lowScoreList.length ? (
                <List
                  dataSource={lowScoreList}
                  renderItem={(item) => (
                    <List.Item>
                      <List.Item.Meta
                        title={
                          <Space>
                            <span>转人工#{item.handoffRequestId}</span>
                            <Tag color="red">{item.score ?? '-'}分</Tag>
                          </Space>
                        }
                        description={
                          <div>
                            <div>用户#{item.userId} / 客服#{item.agentId ?? '-'}</div>
                            <div style={{ color: '#666' }}>{item.comment || '无评价内容'}</div>
                          </div>
                        }
                      />
                    </List.Item>
                  )}
                />
              ) : (
                <Empty description="暂无低分告警" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              )}
            </Card>
          </Col>
        </Row>
      </div>
    </Spin>
  );
}
