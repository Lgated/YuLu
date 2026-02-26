import { useEffect, useMemo, useState } from 'react';
import {
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  message
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { Dayjs } from 'dayjs';
import { adminHandoffApi } from '../../api/adminHandoff';
import type { AgentMonitor, HandoffRatingRecord, HandoffRatingStats, HandoffRecord } from '../../api/types';

const { RangePicker } = DatePicker;

const handoffStatusOptions = ['PENDING', 'ASSIGNED', 'ACCEPTED', 'IN_PROGRESS', 'COMPLETED', 'CLOSED', 'CANCELLED', 'FALLBACK_TICKET'];
const ratingStatusOptions = ['WAITING', 'RATED', 'PROCESSED', 'EXPIRED'];

const statusColor: Record<string, string> = {
  PENDING: 'orange',
  ASSIGNED: 'blue',
  ACCEPTED: 'blue',
  IN_PROGRESS: 'geekblue',
  COMPLETED: 'green',
  CLOSED: 'default',
  CANCELLED: 'red',
  FALLBACK_TICKET: 'purple',
  WAITING: 'orange',
  RATED: 'blue',
  PROCESSED: 'green',
  EXPIRED: 'default',
  ONLINE: 'green',
  AWAY: 'gold',
  OFFLINE: 'default'
};

export default function AdminHandoffPage() {
  const [recordForm] = Form.useForm();
  const [ratingForm] = Form.useForm();

  const [records, setRecords] = useState<HandoffRecord[]>([]);
  const [recordsLoading, setRecordsLoading] = useState(false);
  const [recordPageNo, setRecordPageNo] = useState(1);
  const [recordPageSize, setRecordPageSize] = useState(20);
  const [recordTotal, setRecordTotal] = useState(0);

  const [agentList, setAgentList] = useState<AgentMonitor[]>([]);
  const [agentLoading, setAgentLoading] = useState(false);

  const [ratings, setRatings] = useState<HandoffRatingRecord[]>([]);
  const [ratingsLoading, setRatingsLoading] = useState(false);
  const [ratingPageNo, setRatingPageNo] = useState(1);
  const [ratingPageSize, setRatingPageSize] = useState(20);
  const [ratingTotal, setRatingTotal] = useState(0);
  const [ratingStats, setRatingStats] = useState<HandoffRatingStats>({
    total: 0,
    avgScore: 0,
    positiveCount: 0,
    neutralCount: 0,
    negativeCount: 0,
    positiveRate: 0
  });

  const [processModalOpen, setProcessModalOpen] = useState(false);
  const [processing, setProcessing] = useState(false);
  const [processingRatingId, setProcessingRatingId] = useState<number | null>(null);
  const [processNote, setProcessNote] = useState('');

  useEffect(() => {
    loadRecords();
  }, [recordPageNo, recordPageSize]);

  useEffect(() => {
    loadAgentStatus();
  }, []);

  useEffect(() => {
    loadRatings();
  }, [ratingPageNo, ratingPageSize]);

  const loadRecords = async () => {
    const values = recordForm.getFieldsValue();
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
        pageNo: recordPageNo,
        pageSize: recordPageSize
      });
      if (res.success || res.code === '200') {
        setRecords(res.data?.records || []);
        setRecordTotal(res.data?.total || 0);
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

  const loadRatings = async () => {
    const values = ratingForm.getFieldsValue();
    const range = values?.range as [Dayjs, Dayjs] | undefined;
    const startTime = range?.[0] ? range[0].format('YYYY-MM-DD HH:mm:ss') : undefined;
    const endTime = range?.[1] ? range[1].format('YYYY-MM-DD HH:mm:ss') : undefined;

    setRatingsLoading(true);
    try {
      const [listRes, statsRes] = await Promise.all([
        adminHandoffApi.listRatings({
          agentId: values?.agentId,
          score: values?.score,
          status: values?.status,
          startTime,
          endTime,
          pageNo: ratingPageNo,
          pageSize: ratingPageSize
        }),
        adminHandoffApi.getRatingStats()
      ]);

      if (listRes.success || listRes.code === '200') {
        setRatings(listRes.data?.records || []);
        setRatingTotal(listRes.data?.total || 0);
      }
      if (statsRes.success || statsRes.code === '200') {
        setRatingStats(statsRes.data || ratingStats);
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '加载评价数据失败');
    } finally {
      setRatingsLoading(false);
    }
  };

  const forceAgentStatus = async (agentId: number, status: string) => {
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

  const openProcessModal = (ratingId: number) => {
    setProcessingRatingId(ratingId);
    setProcessNote('');
    setProcessModalOpen(true);
  };

  const submitProcess = async () => {
    if (!processingRatingId) return;
    setProcessing(true);
    try {
      const res = await adminHandoffApi.processRating(processingRatingId, processNote.trim());
      if (res.success || res.code === '200') {
        message.success('处理成功');
        setProcessModalOpen(false);
        loadRatings();
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '处理失败');
    } finally {
      setProcessing(false);
    }
  };

  const recordColumns = useMemo<ColumnsType<HandoffRecord>>(
    () => [
      { title: 'ID', dataIndex: 'handoffRequestId', width: 90 },
      { title: '会话ID', dataIndex: 'sessionId', width: 90 },
      { title: '客户', dataIndex: 'userName', render: (text, row) => text || `客户#${row.userId}` },
      { title: '客服', dataIndex: 'agentName', render: (text, row) => text || (row.agentId ? `客服#${row.agentId}` : '未分配') },
      { title: '状态', dataIndex: 'status', width: 120, render: (v) => <Tag color={statusColor[v] || 'default'}>{v}</Tag> },
      { title: '优先级', dataIndex: 'priority', width: 100 },
      { title: '创建时间', dataIndex: 'createdAt', width: 180 },
      { title: '接入时间', dataIndex: 'acceptedAt', width: 180 },
      { title: '完成时间', dataIndex: 'completedAt', width: 180 },
      { title: '等待(秒)', dataIndex: 'waitDurationSeconds', width: 100 },
      { title: '通话(秒)', dataIndex: 'chatDurationSeconds', width: 100 }
    ],
    []
  );

  const agentColumns = useMemo<ColumnsType<AgentMonitor>>(
    () => [
      { title: '客服ID', dataIndex: 'agentId', width: 100 },
      { title: '客服名称', dataIndex: 'agentName', render: (text, row) => text || `客服#${row.agentId}` },
      { title: '状态', dataIndex: 'status', width: 120, render: (v) => <Tag color={statusColor[v] || 'default'}>{v}</Tag> },
      { title: '会话数', dataIndex: 'currentSessions', width: 120, render: (v, row) => `${v ?? 0}/${row.maxSessions ?? 0}` },
      { title: '最后活跃', dataIndex: 'lastActiveTime', width: 180 },
      {
        title: '操作',
        key: 'actions',
        width: 220,
        render: (_, row) => (
          <Space>
            <Button size="small" onClick={() => forceAgentStatus(row.agentId, 'ONLINE')}>
              在线
            </Button>
            <Button size="small" onClick={() => forceAgentStatus(row.agentId, 'AWAY')}>
              离开
            </Button>
            <Button size="small" danger onClick={() => forceAgentStatus(row.agentId, 'OFFLINE')}>
              离线
            </Button>
          </Space>
        )
      }
    ],
    []
  );

  const ratingColumns = useMemo<ColumnsType<HandoffRatingRecord>>(
    () => [
      { title: '评价ID', dataIndex: 'id', width: 90 },
      { title: '转人工ID', dataIndex: 'handoffRequestId', width: 100 },
      { title: '会话ID', dataIndex: 'sessionId', width: 90 },
      { title: '客户ID', dataIndex: 'userId', width: 90 },
      { title: '客服ID', dataIndex: 'agentId', width: 90 },
      { title: '评分', dataIndex: 'score', width: 90 },
      {
        title: '标签',
        dataIndex: 'tags',
        render: (tags?: string[]) =>
          tags && tags.length ? (
            <Space size={[4, 4]} wrap>
              {tags.map((t) => (
                <Tag key={t}>{t}</Tag>
              ))}
            </Space>
          ) : (
            '-'
          )
      },
      { title: '评价内容', dataIndex: 'comment', ellipsis: true },
      { title: '状态', dataIndex: 'status', width: 120, render: (v) => <Tag color={statusColor[v] || 'default'}>{v}</Tag> },
      { title: '提交时间', dataIndex: 'submitTime', width: 180 },
      { title: '处理备注', dataIndex: 'processedNote', width: 180, ellipsis: true },
      {
        title: '操作',
        key: 'action',
        width: 120,
        render: (_, row) => (
          <Button size="small" type="link" onClick={() => openProcessModal(row.id)}>
            标记已处理
          </Button>
        )
      }
    ],
    []
  );

  return (
    <>
      <Tabs
        items={[
          {
            key: 'records',
            label: '转人工记录',
            children: (
              <Card>
                <Form form={recordForm} layout="inline" style={{ marginBottom: 16 }}>
                  <Form.Item label="客户ID" name="userId">
                    <InputNumber min={1} placeholder="客户ID" />
                  </Form.Item>
                  <Form.Item label="客服ID" name="agentId">
                    <InputNumber min={1} placeholder="客服ID" />
                  </Form.Item>
                  <Form.Item label="状态" name="status">
                    <Select allowClear style={{ width: 170 }} placeholder="状态">
                      {handoffStatusOptions.map((s) => (
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
                      <Button type="primary" onClick={() => { setRecordPageNo(1); loadRecords(); }}>
                        查询
                      </Button>
                      <Button
                        onClick={() => {
                          recordForm.resetFields();
                          setRecordPageNo(1);
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
                    current: recordPageNo,
                    pageSize: recordPageSize,
                    total: recordTotal,
                    showSizeChanger: true,
                    onChange: (page, size) => {
                      setRecordPageNo(page);
                      setRecordPageSize(size || 20);
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
              <Card
                title="客服状态"
                extra={
                  <Button onClick={loadAgentStatus} loading={agentLoading}>
                    刷新
                  </Button>
                }
              >
                <Table rowKey="agentId" columns={agentColumns} dataSource={agentList} loading={agentLoading} pagination={false} scroll={{ x: 900 }} />
              </Card>
            )
          },
          {
            key: 'ratings',
            label: '满意度评价',
            children: (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                <Card>
                  <Space size={24}>
                    <Statistic title="评价总数" value={ratingStats.total} />
                    <Statistic title="平均评分" value={ratingStats.avgScore || 0} precision={2} />
                    <Statistic title="好评率" value={ratingStats.positiveRate || 0} precision={2} suffix="%" />
                    <Statistic title="好评(>=4)" value={ratingStats.positiveCount} />
                    <Statistic title="中评(=3)" value={ratingStats.neutralCount} />
                    <Statistic title="差评(<=2)" value={ratingStats.negativeCount} />
                  </Space>
                </Card>

                <Card>
                  <Form form={ratingForm} layout="inline" style={{ marginBottom: 16 }}>
                    <Form.Item label="客服ID" name="agentId">
                      <InputNumber min={1} placeholder="客服ID" />
                    </Form.Item>
                    <Form.Item label="评分" name="score">
                      <Select allowClear style={{ width: 120 }} placeholder="评分">
                        {[1, 2, 3, 4, 5].map((s) => (
                          <Select.Option key={s} value={s}>
                            {s}
                          </Select.Option>
                        ))}
                      </Select>
                    </Form.Item>
                    <Form.Item label="状态" name="status">
                      <Select allowClear style={{ width: 140 }} placeholder="状态">
                        {ratingStatusOptions.map((s) => (
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
                        <Button type="primary" onClick={() => { setRatingPageNo(1); loadRatings(); }}>
                          查询
                        </Button>
                        <Button
                          onClick={() => {
                            ratingForm.resetFields();
                            setRatingPageNo(1);
                            loadRatings();
                          }}
                        >
                          重置
                        </Button>
                      </Space>
                    </Form.Item>
                  </Form>

                  <Table
                    rowKey="id"
                    columns={ratingColumns}
                    dataSource={ratings}
                    loading={ratingsLoading}
                    scroll={{ x: 1500 }}
                    pagination={{
                      current: ratingPageNo,
                      pageSize: ratingPageSize,
                      total: ratingTotal,
                      showSizeChanger: true,
                      onChange: (page, size) => {
                        setRatingPageNo(page);
                        setRatingPageSize(size || 20);
                      }
                    }}
                  />
                </Card>
              </div>
            )
          }
        ]}
      />

      <Modal
        title="处理评价反馈"
        open={processModalOpen}
        onCancel={() => setProcessModalOpen(false)}
        onOk={submitProcess}
        okText="确认处理"
        cancelText="取消"
        confirmLoading={processing}
      >
        <Input.TextArea
          rows={4}
          value={processNote}
          onChange={(e) => setProcessNote(e.target.value)}
          maxLength={500}
          placeholder="请输入处理备注（可选）"
        />
      </Modal>
    </>
  );
}

