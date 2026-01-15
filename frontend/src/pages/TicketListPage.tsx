import { useEffect, useState } from 'react';
import { Card, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ticketApi } from '../api/ticket';
import type { Ticket } from '../api/types';

export default function TicketListPage() {
  const [data, setData] = useState<Ticket[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    load();
  }, []);

  const load = async () => {
    setLoading(true);
    try {
      const res = await ticketApi.list({ page: 1, size: 20 });
      if (res.success || res.code === '200') {
        setData(res.data.records || []);
      }
    } finally {
      setLoading(false);
    }
  };

  const columns: ColumnsType<Ticket> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '标题', dataIndex: 'title' },
    {
      title: '优先级',
      dataIndex: 'priority',
      width: 120,
      render: (p: string) => {
        const color = p === 'HIGH' || p === 'URGENT' ? 'red' : p === 'MEDIUM' ? 'orange' : 'default';
        return <Tag color={color}>{p}</Tag>;
      }
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      render: (s: string) => <Tag>{s}</Tag>
    },
    { title: '创建时间', dataIndex: 'createTime', width: 180 }
  ];

  return (
    <Card title="工单中心">
      <Table<Ticket> rowKey="id" loading={loading} dataSource={data} columns={columns} />
    </Card>
  );
}


