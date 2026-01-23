import { useState, useEffect } from 'react';
import { Card, Table, Tag, Button, Space, message } from 'antd';
import { ticketApi } from '../../api/ticket';
import type { Ticket } from '../../api/types';
import { useHeartbeat } from '../../hooks/useHeartbeat';

export default function AgentTicketPage() {
  // 启动心跳定时器（客服保持在线状态）
  useHeartbeat({ enabled: true, interval: 30000 }); // 30秒一次
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0
  });

  const loadTickets = async (page = 1, pageSize = 10, status?: string) => {
    setLoading(true);
    try {
      const res = await ticketApi.list({
        page,
        size: pageSize,
        status
      });
      if (res.success || res.code === '200') {
        setTickets(res.data.records || []);
        setPagination({
          current: res.data.current || 1,
          pageSize: res.data.size || 10,
          total: res.data.total || 0
        });
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '加载工单失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadTickets();
  }, []);

  const handleStatusChange = async (ticketId: number, targetStatus: string) => {
    try {
      await ticketApi.transition(ticketId, targetStatus, '');
      message.success('状态更新成功');
      loadTickets(pagination.current, pagination.pageSize);
    } catch (e: any) {
      message.error(e?.response?.data?.message || '操作失败');
    }
  };

  const columns = [
    {
      title: '工单ID',
      dataIndex: 'id',
      key: 'id',
      width: 80
    },
    {
      title: '标题',
      dataIndex: 'title',
      key: 'title',
      ellipsis: true
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string) => {
        const colorMap: Record<string, string> = {
          PENDING: 'orange',
          PROCESSING: 'blue',
          DONE: 'green',
          CLOSED: 'default'
        };
        const textMap: Record<string, string> = {
          PENDING: '待处理',
          PROCESSING: '处理中',
          DONE: '已完成',
          CLOSED: '已关闭'
        };
        return <Tag color={colorMap[status]}>{textMap[status] || status}</Tag>;
      }
    },
    {
      title: '优先级',
      dataIndex: 'priority',
      key: 'priority',
      width: 100,
      render: (priority: string) => {
        const colorMap: Record<string, string> = {
          LOW: 'default',
          MEDIUM: 'blue',
          HIGH: 'orange',
          URGENT: 'red'
        };
        return <Tag color={colorMap[priority]}>{priority}</Tag>;
      }
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_: any, record: Ticket) => (
        <Space>
          <Button 
            type="link" 
            onClick={() => handleStatusChange(record.id, 'PROCESSING')}
            disabled={record.status === 'PROCESSING'}
          >
            开始处理
          </Button>
          <Button 
            type="link" 
            onClick={() => handleStatusChange(record.id, 'DONE')}
            disabled={record.status === 'DONE' || record.status === 'CLOSED'}
          >
            完成
          </Button>
        </Space>
      )
    }
  ];

  return (
    <Card title="我的工单">
      <Table
        columns={columns}
        dataSource={tickets}
        rowKey="id"
        loading={loading}
        pagination={{
          current: pagination.current,
          pageSize: pagination.pageSize,
          total: pagination.total,
          onChange: (page, pageSize) => loadTickets(page, pageSize),
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 条`
        }}
      />
    </Card>
  );
}