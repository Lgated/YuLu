import { Card, Empty } from 'antd';

export default function CustomerFaqPage() {
  return (
    <Card title="常见问题">
      <Empty description="FAQ 待接入：/api/customer/faq/list" />
    </Card>
  );
}


