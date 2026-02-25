import { useEffect, useMemo, useState } from 'react';
import { Button, Card, DatePicker, Form, InputNumber, Select, Space, Table, Tag, Tabs, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { Dayjs } from 'dayjs';
import { adminHandoffApi } from '../../api/adminHandoff';
import type { AgentMonitor, HandoffRecord } from '../../api/types';

const { RangePicker } = DatePicker;

const statusOptions = [
  'PENDING',
  'ASSIGNED',
  'ACCEPTED',
  'IN_PROGRESS',
  'COMPLETED',
  'CLOSED',
  'CANCELLED',
  'FALLBACK_TICKET'
];

const statusColor: Record<string, string> = {
  PENDING: 'orange',
  ASSIGNED: 'blue',
  ACCEPTED: 'blue',
  IN_PROGRESS: 'geekblue',
  COMPLETED: 'green',
  CLOSED: 'default',
  CANCELLED: 'red',
  FALLBACK_TICKET: 'purple'
};

export default function AdminHandoffPage() {
  const [records, setRecords] = useState<HandoffRecord[]>([]);
  const [recordsLoading, setRecordsLoading] = useState(false);
  const [agentList, setAgentList] = useState<AgentMonitor[]>([]);
  const [agentLoading, setAgentLoading] = useState(false);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [queryForm] = Form.useForm();

  useEffect(() => {
    loadRecords();
  }, [pageNo, pageSize]);

  useEffect(() => {
    loadAgentStatus();
  }, []);

  const loadRecords = async () => {
    const values = queryForm.getFieldsValue();
    const range = values?.range as [Dayjs, Dayjs] | undefined;
    const startTime = range?.[0] ? range[0].format('YYYY-MM-DD HH:mm:ss') : undefined;
    const endTime = range?.[1] ? range[1].format('YYYY-MM-DD HH:mm:ss') : undefined;

    setRecordsLoading(true);
    try {
      const res = await adminHandoffApi.listRecords({
        userId: values?.userId,
        agentId: values?.agentId,
        status: values?.status,
        startTime,
        endTime,
        pageNo,
        pageSize
      });
      if (res.success || res.code === '200') {
        setRecords(res.data?.records || []);
        setTotal(res.data?.total || 0);
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '加载转人工记录失败');
    } finally {
      setRecordsLoading(false);
    }
  };

  const loadAgentStatus = async () => {
    setAgentLoading(true);
    try {
      const res = await adminHandoffApi.getAgentStatus();
      if (res.success || res.code === '200') {
        setAgentList(res.data || []);
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '加载客服状态失败');
    } finally {
      setAgentLoading(false);
    }
  };

  const handleForceStatus = async (agentId: number, status: string) => {
    try {
      const res = await adminHandoffApi.forceAgentStatus(agentId, status);
      if (res.success || res.code === '200') {
        message.success('状态已更新');
        loadAgentStatus();
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '更新状态失败');
    }
  };


  const recordColumns = useMemo<ColumnsType<HandoffRecord>>(
    () => [
      {
        title: 'ID',
        dataIndex: 'handoffRequestId',
        width: 90
      },
      {
        title: '会话ID',
        dataIndex: 'sessionId',
        width: 90
      },
      {
        title: '客户',
        dataIndex: 'userName',
        render: (text, record) => text || `客户 #${record.userId}`
      },
      {
        title: '客服',
        dataIndex: 'agentName',
        render: (text, record) => text || (record.agentId ? `客服 #${record.agentId}` : '未分配')
      },
      {
        title: '状态',
        dataIndex: 'status',
        width: 130,
        render: (value) => <Tag color={statusColor[value] || 'default'}>{value}</Tag>
      },
      {
        title: '优先级',
        dataIndex: 'priority',
        width: 100
      },
      {
        title: '创建时间',
        dataIndex: 'createdAt',
        width: 180
      },
      {
        title: '接入时间',
        dataIndex: 'acceptedAt',
        width: 180
      },
      {
        title: '完成时间',
        dataIndex: 'completedAt',
        width: 180
      },
      {
        title: '等待(秒)',
        dataIndex: 'waitDurationSeconds',
        width: 100
      },
      {
        title: '通话(秒)',
        dataIndex: 'chatDurationSeconds',
        width: 100
      }
    ],
    []
  );

  const agentColumns = useMemo<ColumnsType<AgentMonitor>>(
    () => [
      {
        title: '客服ID',
        dataIndex: 'agentId',
        width: 100
      },
      {
        title: '客服名称',
        dataIndex: 'agentName',
        render: (text, record) => text || `客服 #${record.agentId}`
      },
      {
        title: '状态',
        dataIndex: 'status',
        width: 120,
        render: (value) => <Tag color={statusColor[value] || 'default'}>{value}</Tag>
      },
      {
        title: '会话数',
        dataIndex: 'currentSessions',
        width: 120,
        render: (value, record) => `${value ?? 0}/${record.maxSessions ?? 0}`
      },
      {
        title: '最后活跃',
        dataIndex: 'lastActiveTime',
        width: 180
      },
      {
        title: '操作',
        key: 'actions',
        width: 220,
        render: (_, record) => (
          <Space>
            <Button size="small" onClick={() => handleForceStatus(record.agentId, 'ONLINE')}>
              在线
            </Button>
            <Button size="small" onClick={() => handleForceStatus(record.agentId, 'AWAY')}>
              离开
            </Button>
            <Button size="small" danger onClick={() => handleForceStatus(record.agentId, 'OFFLINE')}>
              离线
            </Button>
          </Space>
        )
      }
    ],
    []
  );

  return (
    <Tabs
      items={[
        {
          key: 'records',
          label: '转人工记录',
          children: (
            <Card>
              <Form form={queryForm} layout="inline" style={{ marginBottom: 16 }}>
                <Form.Item label="客户ID" name="userId">
                  <InputNumber min={1} placeholder="客户ID" />
                </Form.Item>
                <Form.Item label="客服ID" name="agentId">
                  <InputNumber min={1} placeholder="客服ID" />
                </Form.Item>
                <Form.Item label="状态" name="status">
                  <Select allowClear style={{ width: 180 }} placeholder="状态">
                    {statusOptions.map((s) => (
                      <Select.Option key={s} value={s}>
                        {s}
                      </Select.Option>
                    ))}
                  </Select>
                </Form.Item>
                <Form.Item label="时间" name="range">
                  <RangePicker showTime />
                </Form.Item>
                <Form.Item>
                  <Space>
                    <Button type="primary" onClick={() => { setPageNo(1); loadRecords(); }}>
                      查询
                    </Button>
                    <Button
                      onClick={() => {
                        queryForm.resetFields();
                        setPageNo(1);
                        loadRecords();
                      }}
                    >
                      重置
                    </Button>
                  </Space>
                </Form.Item>
              </Form>

              <Table
                rowKey="handoffRequestId"
                columns={recordColumns}
                dataSource={records}
                loading={recordsLoading}
                scroll={{ x: 1200 }}
                pagination={{
                  current: pageNo,
                  pageSize,
                  total,
                  showSizeChanger: true,
                  onChange: (page, size) => {
                    setPageNo(page);
                    setPageSize(size || 20);
                  }
                }}
              />
            </Card>
          )
        },
        {
          key: 'agents',
          label: '客服看板',
          children: (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <Card
                title="客服状态"
                extra={
                  <Button onClick={loadAgentStatus} loading={agentLoading}>
                    刷新
                  </Button>
                }
              >
                <Table
                  rowKey="agentId"
                  columns={agentColumns}
                  dataSource={agentList}
                  loading={agentLoading}
                  pagination={false}
                  scroll={{ x: 900 }}
                />
              </Card>
            </div>
          )
        }
      ]}
    />
  );
}
