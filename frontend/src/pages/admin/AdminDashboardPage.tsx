import { Card, Row, Col, Statistic } from 'antd';

export default function AdminDashboardPage() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Row gutter={16}>
        <Col span={6}>
          <Card>
            <Statistic title="今日会话" value={0} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="待处理工单" value={0} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="在线客服" value={0} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="满意度" value={0} suffix="%" />
          </Card>
        </Col>
      </Row>
      <Card title="说明">
        这里是租户端（B端）首页数据看板占位页；后续可对接后端统计接口（工单统计/会话统计/客服在线等）。
      </Card>
    </div>
  );
}


