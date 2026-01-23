import { useEffect, useMemo, useState } from 'react';
import {
  Card,
  Table,
  Tag,
  Space,
  Button,
  Modal,
  Select,
  message,
  Drawer,
  Descriptions,
  Form,
  Input,
  Divider
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  EyeOutlined,
  UserSwitchOutlined,
  ReloadOutlined,
  CommentOutlined
} from '@ant-design/icons';
import { ticketApi } from '../../api/ticket';
import type { Ticket, TicketComment, UserResponse } from '../../api/types';
import { getRole } from '../../utils/storage';

export default function TicketListPage() {
  const [data, setData] = useState<Ticket[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [statusFilter, setStatusFilter] = useState<string | undefined>(undefined);

  // 派单
  const [assignModalOpen, setAssignModalOpen] = useState(false);
  const [assignLoading, setAssignLoading] = useState(false);
  const [agentsLoading, setAgentsLoading] = useState(false);
  const [agents, setAgents] = useState<UserResponse[]>([]);
  const [assignTicket, setAssignTicket] = useState<Ticket | null>(null);
  const [assignForm] = Form.useForm();

  // 详情/备注
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerTicket, setDrawerTicket] = useState<Ticket | null>(null);
  const [commentsLoading, setCommentsLoading] = useState(false);
  const [comments, setComments] = useState<TicketComment[]>([]);
  const [commentForm] = Form.useForm();

  useEffect(() => {
    load(1, pagination.pageSize, statusFilter);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const role = getRole();
  const isAdmin = role === 'ADMIN';

  const load = async (page = 1, size = 10, status?: string) => {
    setLoading(true);
    try {
      const res = await ticketApi.list({ page, size, status });
      if (res.success || res.code === '200') {
        const records = res.data?.records || [];
        setData(records);
        setPagination({
          current: res.data?.current || page,
          pageSize: res.data?.size || size,
          total: res.data?.total || 0
        });
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '加载工单列表失败');
    } finally {
      setLoading(false);
    }
  };

  const statusOptions = useMemo(
    () => [
      { label: '全部', value: 'ALL' },
      { label: 'PENDING', value: 'PENDING' },
      { label: 'PROCESSING', value: 'PROCESSING' },
      { label: 'DONE', value: 'DONE' },
      { label: 'CLOSED', value: 'CLOSED' }
    ],
    []
  );

  const openAssign = async (ticket: Ticket) => {
    if (!isAdmin) {
      message.warning('只有管理员可以派单');
      return;
    }
    setAssignTicket(ticket);
    setAssignModalOpen(true);
    assignForm.resetFields();

    // 拉客服列表
    setAgentsLoading(true);
    try {
      const res = await ticketApi.getAgents();
      if (res.success || res.code === '200') {
        setAgents((res.data || []) as any);
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '加载客服列表失败');
    } finally {
      setAgentsLoading(false);
    }
  };

  const submitAssign = async () => {
    if (!assignTicket) return;
    try {
      const values = await assignForm.validateFields();
      const assigneeUserId = Number(values.assigneeUserId);
      if (!assigneeUserId) {
        message.warning('请选择客服');
        return;
      }
      Modal.confirm({
        title: '确认派单',
        content: `确定将工单 #${assignTicket.id} 派给所选客服吗？`,
        okText: '确定',
        cancelText: '取消',
        onOk: async () => {
          setAssignLoading(true);
          try {
            const res = await ticketApi.assign({ ticketId: assignTicket.id, assigneeUserId });
            if (res.success || res.code === '200') {
              message.success('派单成功');
              setAssignModalOpen(false);
              setAssignTicket(null);
              await load(pagination.current, pagination.pageSize, statusFilter);
            }
          } catch (e: any) {
            message.error(e?.response?.data?.message || '派单失败');
          } finally {
            setAssignLoading(false);
          }
        }
      });
    } catch {
      // validation error
    }
  };

  const openDrawer = async (ticket: Ticket) => {
    setDrawerTicket(ticket);
    setDrawerOpen(true);
    commentForm.resetFields();
    await loadComments(ticket.id);
  };

  const loadComments = async (ticketId: number) => {
    setCommentsLoading(true);
    try {
      const res = await ticketApi.comments(ticketId);
      if (res.success || res.code === '200') {
        setComments(res.data || []);
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '加载备注失败');
    } finally {
      setCommentsLoading(false);
    }
  };

  const addComment = async () => {
    if (!drawerTicket) return;
    try {
      const values = await commentForm.validateFields();
      const content = String(values.content || '').trim();
      if (!content) {
        message.warning('请输入备注内容');
        return;
      }
      const res = await ticketApi.addComment(drawerTicket.id, content);
      if (res.success || res.code === '200') {
        message.success('添加备注成功');
        commentForm.resetFields();
        await loadComments(drawerTicket.id);
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '添加备注失败');
    }
  };

  const transition = async (ticket: Ticket, targetStatus: string) => {
    Modal.confirm({
      title: '确认状态流转',
      content: `确定将工单 #${ticket.id} 状态变更为 ${targetStatus} 吗？`,
      okText: '确定',
      cancelText: '取消',
      onOk: async () => {
        try {
          const res = await ticketApi.transition(ticket.id, targetStatus, '');
          if (res.success || res.code === '200') {
            message.success('状态更新成功');
            await load(pagination.current, pagination.pageSize, statusFilter);
            // 如果抽屉打开，更新一下抽屉里的 ticket
            if (drawerTicket?.id === ticket.id) {
              setDrawerTicket({ ...ticket, status: targetStatus });
            }
          }
        } catch (e: any) {
          message.error(e?.response?.data?.message || '状态更新失败');
        }
      }
    });
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
    { title: '创建时间', dataIndex: 'createTime', width: 180 },
    {
      title: '操作',
      key: 'action',
      width: 260,
      render: (_: any, record: Ticket) => (
        <Space>
          <Button type="link" icon={<EyeOutlined />} onClick={() => openDrawer(record)}>
            查看
          </Button>
          {isAdmin && !record.assignee && (
            <Button type="link" icon={<UserSwitchOutlined />} onClick={() => openAssign(record)}>
              派单
            </Button>
          )}
          <Button
            type="link"
            icon={<CommentOutlined />}
            onClick={() => openDrawer(record)}
          >
            备注
          </Button>
        </Space>
      )
    }
  ];

  return (
    <Card
      title="工单中心"
      extra={
        <Space>
          <Select
            style={{ width: 180 }}
            placeholder="按状态筛选"
            value={statusFilter || 'ALL'}
            options={statusOptions}
            onChange={(v) => {
              const next = v === 'ALL' ? undefined : v;
              setStatusFilter(next);
              load(1, pagination.pageSize, next);
            }}
          />
          <Button
            icon={<ReloadOutlined />}
            onClick={() => load(pagination.current, pagination.pageSize, statusFilter)}
          >
            刷新
          </Button>
        </Space>
      }
    >
      <Table<Ticket>
        rowKey="id"
        loading={loading}
        dataSource={data}
        columns={columns}
        pagination={{
          current: pagination.current,
          pageSize: pagination.pageSize,
          total: pagination.total,
          onChange: (page, pageSize) => {
            setPagination({ ...pagination, current: page, pageSize });
            load(page, pageSize, statusFilter);
          },
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 条`
        }}
      />

      {/* 派单弹窗 */}
      <Modal
        title={assignTicket ? `派单 - 工单 #${assignTicket.id}` : '派单'}
        open={assignModalOpen}
        onCancel={() => {
          setAssignModalOpen(false);
          setAssignTicket(null);
        }}
        okText="确定"
        cancelText="取消"
        confirmLoading={assignLoading}
        onOk={submitAssign}
        destroyOnClose
      >
        <Form form={assignForm} layout="vertical">
          <Form.Item
            label="选择客服"
            name="assigneeUserId"
            rules={[{ required: true, message: '请选择客服' }]}
          >
            <Select
              placeholder="请选择客服"
              loading={agentsLoading}
              options={agents.map((a) => ({
                label: `${a.username}${a.nickName ? ` (${a.nickName})` : ''}`,
                value: a.id
              }))}
              showSearch
              optionFilterProp="label"
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 工单详情抽屉 */}
      <Drawer
        title={drawerTicket ? `工单详情 - #${drawerTicket.id}` : '工单详情'}
        open={drawerOpen}
        width={720}
        onClose={() => {
          setDrawerOpen(false);
          setDrawerTicket(null);
          setComments([]);
        }}
        destroyOnClose
      >
        {drawerTicket && (
          <>
            <Descriptions bordered column={2} size="small">
              <Descriptions.Item label="标题" span={2}>
                {drawerTicket.title}
              </Descriptions.Item>
              <Descriptions.Item label="状态">{drawerTicket.status}</Descriptions.Item>
              <Descriptions.Item label="优先级">{drawerTicket.priority}</Descriptions.Item>
              <Descriptions.Item label="客户ID">{drawerTicket.userId}</Descriptions.Item>
              <Descriptions.Item label="分配人">{drawerTicket.assignee ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="创建时间" span={2}>
                {drawerTicket.createTime}
              </Descriptions.Item>
              <Descriptions.Item label="描述" span={2}>
                {drawerTicket.description || '-'}
              </Descriptions.Item>
            </Descriptions>

            <Divider />

            <Space>
              <Button
                onClick={() => transition(drawerTicket, 'PROCESSING')}
                disabled={drawerTicket.status === 'PROCESSING' || drawerTicket.status === 'DONE' || drawerTicket.status === 'CLOSED'}
              >
                开始处理
              </Button>
              <Button
                type="primary"
                onClick={() => transition(drawerTicket, 'DONE')}
                disabled={drawerTicket.status === 'DONE' || drawerTicket.status === 'CLOSED'}
              >
                完成
              </Button>
              <Button
                danger
                onClick={() => transition(drawerTicket, 'CLOSED')}
                disabled={drawerTicket.status === 'CLOSED'}
              >
                关闭
              </Button>
              <Button onClick={() => loadComments(drawerTicket.id)} loading={commentsLoading}>
                刷新备注
              </Button>
            </Space>

            <Divider />

            <Form form={commentForm} layout="vertical">
              <Form.Item
                label="添加备注"
                name="content"
                rules={[{ required: true, message: '请输入备注内容' }]}
              >
                <Input.TextArea rows={3} placeholder="请输入备注内容（将记录到工单备注）" />
              </Form.Item>
              <Form.Item>
                <Button type="primary" onClick={addComment}>
                  提交备注
                </Button>
              </Form.Item>
            </Form>

            <Divider />

            <div style={{ fontWeight: 500, marginBottom: 8 }}>备注列表</div>
            <Table<TicketComment>
              rowKey="id"
              loading={commentsLoading}
              dataSource={comments}
              pagination={false}
              size="small"
              columns={[
                { title: 'ID', dataIndex: 'id', width: 80 },
                { title: '内容', dataIndex: 'content' },
                { title: '用户', dataIndex: 'userId', width: 100 },
                { title: '时间', dataIndex: 'createTime', width: 180 }
              ]}
            />
          </>
        )}
      </Drawer>
    </Card>
  );
}


