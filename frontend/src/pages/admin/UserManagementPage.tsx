import { useState, useEffect } from 'react';
import {
    Card,
    Tabs,
    Table,
    Button,
    Space,
    Tag,
    message,
    Modal,
    Form,
    Input,
    Select,
    Popconfirm,
    Switch,
    Tooltip
} from 'antd';
import {
    PlusOutlined,
    EditOutlined,
    DeleteOutlined,
    KeyOutlined,
    UserOutlined,
    TeamOutlined
} from '@ant-design/icons';
import { userManagementApi } from '../../api/userManagement';
import type { UserResponse, CreateUserRequest, UpdateUserRequest } from '../../api/types';
import { getUserId } from '../../utils/storage';

const { TabPane } = Tabs;

export default function UserManagementPage() {
    const [activeTab, setActiveTab] = useState<'customer' | 'staff'>('customer');
    const [customerUsers, setCustomerUsers] = useState<UserResponse[]>([]);
    const [staffUsers, setStaffUsers] = useState<UserResponse[]>([]);
    const [loading, setLoading] = useState(false);
    const [pagination, setPagination] = useState({
        current: 1,
        pageSize: 10,
        total: 0
    });

    // 弹窗状态
    const [createModalVisible, setCreateModalVisible] = useState(false);
    const [editModalVisible, setEditModalVisible] = useState(false);
    const [resetPasswordModalVisible, setResetPasswordModalVisible] = useState(false);
    const [selectedUser, setSelectedUser] = useState<UserResponse | null>(null);
    const [form] = Form.useForm();
    const [passwordForm] = Form.useForm();

    // 加载用户列表
    const loadUsers = async (page = 1, pageSize = 10, keyword?: string) => {
        setLoading(true);
        try {
            const role = activeTab === 'customer' ? 'USER' : undefined; // C端用户固定为USER
            const res = await userManagementApi.list({
                role,
                status: undefined, // 查询所有状态
                keyword,
                page,
                size: pageSize
            });

            if (res.success || res.code === '200') {
                const users = res.data.records || [];
                if (activeTab === 'customer') {
                    setCustomerUsers(users);
                } else {
                    // B端用户：过滤出ADMIN和AGENT
                    setStaffUsers(users.filter(u => u.role === 'ADMIN' || u.role === 'AGENT'));
                }
                setPagination({
                    current: res.data.current || 1,
                    pageSize: res.data.size || 10,
                    total: res.data.total || 0
                });
            }
        } catch (e: any) {
            message.error(e?.response?.data?.message || '加载用户列表失败');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadUsers();
    }, [activeTab]);

    // 创建用户
    const handleCreate = async (values: CreateUserRequest) => {
        try {
            // 如果是C端用户，固定角色为USER
            if (activeTab === 'customer') {
                values.role = 'USER';
            }

            const res = await userManagementApi.create(values);
            if (res.success || res.code === '200') {
                message.success('创建用户成功');
                setCreateModalVisible(false);
                form.resetFields();
                loadUsers(pagination.current, pagination.pageSize);
            }
        } catch (e: any) {
            message.error(e?.response?.data?.message || '创建用户失败');
        }
    };

    // 编辑用户
    const handleEdit = async (values: UpdateUserRequest) => {
        if (!selectedUser) return;

        try {
            const res = await userManagementApi.update(selectedUser.id, values);
            if (res.success || res.code === '200') {
                message.success('更新用户成功');
                setEditModalVisible(false);
                setSelectedUser(null);
                form.resetFields();
                loadUsers(pagination.current, pagination.pageSize);
            }
            if (res.code == "FORBIDDEN") {
                message.error("不能禁用自己的账号")
            }
        } catch (e: any) {
            message.error(e?.response?.data?.message || '更新用户失败');
        }
    };

    // 重置密码
    const handleResetPassword = async (values: { newPassword: string }) => {
        if (!selectedUser) return;

        try {
            const res = await userManagementApi.resetPassword(selectedUser.id, {
                newPassword: values.newPassword
            });
            if (res.success || res.code === '200') {
                message.success('重置密码成功');
                setResetPasswordModalVisible(false);
                setSelectedUser(null);
                passwordForm.resetFields();
            }
        } catch (e: any) {
            message.error(e?.response?.data?.message || '重置密码失败');
        }
    };

    // 删除用户
    const handleDelete = async (userId: number) => {
        try {
            const res = await userManagementApi.delete(userId);
            if (res.success || res.code === '200') {
                message.success('删除用户成功');
                loadUsers(pagination.current, pagination.pageSize);
            }
        } catch (e: any) {
            message.error(e?.response?.data?.message || '删除用户失败');
        }
    };

    // 更新状态
    const handleStatusChange = async (userId: number, status: number) => {
        // 检查是否是自己的账号
        const currentUserId = getUserId();
        if (currentUserId !== null && userId === currentUserId) {
            message.warning('不能修改自己的账号状态');
            return;
        }

        try {
            const res = await userManagementApi.updateStatus(userId, status);
            if (res.success || res.code === '200') {
                message.success(status === 1 ? '启用用户成功' : '禁用用户成功');
                loadUsers(pagination.current, pagination.pageSize);
            }
            if (res.code == "FORBIDDEN") {
                message.error("不能禁用自己的账号")
            }
        } catch (e: any) {
            message.error(e?.response?.data?.message || '操作失败');
        }
    };

    // 表格列定义（C端用户）
    const customerColumns = [
        {
            title: 'ID',
            dataIndex: 'id',
            key: 'id',
            width: 80
        },
        {
            title: '用户名',
            dataIndex: 'username',
            key: 'username',
            width: 150
        },
        {
            title: '昵称',
            dataIndex: 'nickName',
            key: 'nickName',
            width: 120
        },
        {
            title: '邮箱',
            dataIndex: 'email',
            key: 'email',
            width: 180
        },
        {
            title: '手机号',
            dataIndex: 'phone',
            key: 'phone',
            width: 120
        },
        {
            title: '状态',
            dataIndex: 'status',
            key: 'status',
            width: 100,
            render: (status: number, record: UserResponse) => {
                const currentUserId = getUserId();
                const isSelf = currentUserId !== null && record.id === currentUserId;
                return (
                    <Tooltip title={isSelf ? '不能修改自己的账号状态' : ''}>
                        <Switch
                            checked={status === 1}
                            onChange={(checked) => handleStatusChange(record.id, checked ? 1 : 0)}
                            disabled={isSelf}
                        />
                    </Tooltip>
                );
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
            render: (_: any, record: UserResponse) => (
                <Space>
                    <Button
                        type="link"
                        icon={<EditOutlined />}
                        onClick={() => {
                            setSelectedUser(record);
                            form.setFieldsValue({
                                nickName: record.nickName,
                                email: record.email,
                                phone: record.phone,
                                status: record.status
                            });
                            setEditModalVisible(true);
                        }}
                    >
                        编辑
                    </Button>
                    <Popconfirm
                        title="确定删除此用户吗？"
                        onConfirm={() => handleDelete(record.id)}
                    >
                        <Button type="link" danger icon={<DeleteOutlined />}>
                            删除
                        </Button>
                    </Popconfirm>
                </Space>
            )
        }
    ];

    // 表格列定义（B端用户）
    const staffColumns = [
        {
            title: 'ID',
            dataIndex: 'id',
            key: 'id',
            width: 80
        },
        {
            title: '用户名',
            dataIndex: 'username',
            key: 'username',
            width: 150
        },
        {
            title: '角色',
            dataIndex: 'role',
            key: 'role',
            width: 100,
            render: (role: string) => {
                const colorMap: Record<string, string> = {
                    ADMIN: 'red',
                    AGENT: 'blue'
                };
                const textMap: Record<string, string> = {
                    ADMIN: '管理员',
                    AGENT: '客服'
                };
                return <Tag color={colorMap[role]}>{textMap[role] || role}</Tag>;
            }
        },
        {
            title: '昵称',
            dataIndex: 'nickName',
            key: 'nickName',
            width: 120
        },
        {
            title: '邮箱',
            dataIndex: 'email',
            key: 'email',
            width: 180
        },
        {
            title: '手机号',
            dataIndex: 'phone',
            key: 'phone',
            width: 120
        },
        {
            title: '状态',
            dataIndex: 'status',
            key: 'status',
            width: 100,
            render: (status: number, record: UserResponse) => {
                const currentUserId = getUserId();
                const isSelf = currentUserId !== null && record.id === currentUserId;
                return (
                    <Tooltip title={isSelf ? '不能修改自己的账号状态' : ''}>
                        <Switch
                            checked={status === 1}
                            onChange={(checked) => handleStatusChange(record.id, checked ? 1 : 0)}
                            disabled={isSelf}
                        />
                    </Tooltip>
                );
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
            width: 300,
            render: (_: any, record: UserResponse) => (
                <Space>
                    <Button
                        type="link"
                        icon={<EditOutlined />}
                        onClick={() => {
                            setSelectedUser(record);
                            form.setFieldsValue({
                                nickName: record.nickName,
                                email: record.email,
                                phone: record.phone,
                                role: record.role,
                                status: record.status
                            });
                            setEditModalVisible(true);
                        }}
                    >
                        编辑
                    </Button>
                    <Button
                        type="link"
                        icon={<KeyOutlined />}
                        onClick={() => {
                            setSelectedUser(record);
                            setResetPasswordModalVisible(true);
                        }}
                    >
                        重置密码
                    </Button>
                    <Popconfirm
                        title="确定删除此用户吗？"
                        onConfirm={() => handleDelete(record.id)}
                    >
                        <Button type="link" danger icon={<DeleteOutlined />}>
                            删除
                        </Button>
                    </Popconfirm>
                </Space>
            )
        }
    ];

    const currentUsers = activeTab === 'customer' ? customerUsers : staffUsers;
    const currentColumns = activeTab === 'customer' ? customerColumns : staffColumns;

    return (
        <Card>
            <Tabs
                activeKey={activeTab}
                onChange={(key) => {
                    setActiveTab(key as 'customer' | 'staff');
                    setPagination({ ...pagination, current: 1 });
                }}
                tabBarExtraContent={
                    <Button
                        type="primary"
                        icon={<PlusOutlined />}
                        onClick={() => {
                            form.resetFields();
                            setCreateModalVisible(true);
                        }}
                    >
                        新建{activeTab === 'customer' ? '用户' : '账号'}
                    </Button>
                }
            >
                <TabPane
                    tab={
                        <span>
                            <UserOutlined />
                            C端用户管理
                        </span>
                    }
                    key="customer"
                >
                    <Table
                        columns={currentColumns}
                        dataSource={currentUsers}
                        rowKey="id"
                        loading={loading}
                        pagination={{
                            current: pagination.current,
                            pageSize: pagination.pageSize,
                            total: pagination.total,
                            onChange: (page, pageSize) => loadUsers(page, pageSize),
                            showSizeChanger: true,
                            showTotal: (total) => `共 ${total} 条`
                        }}
                    />
                </TabPane>

                <TabPane
                    tab={
                        <span>
                            <TeamOutlined />
                            B端用户管理（管理员/客服）
                        </span>
                    }
                    key="staff"
                >
                    <Table
                        columns={currentColumns}
                        dataSource={currentUsers}
                        rowKey="id"
                        loading={loading}
                        pagination={{
                            current: pagination.current,
                            pageSize: pagination.pageSize,
                            total: pagination.total,
                            onChange: (page, pageSize) => loadUsers(page, pageSize),
                            showSizeChanger: true,
                            showTotal: (total) => `共 ${total} 条`
                        }}
                    />
                </TabPane>
            </Tabs>

            {/* 创建用户弹窗 */}
            <Modal
                title={activeTab === 'customer' ? '创建C端用户' : '创建B端账号'}
                open={createModalVisible}
                onCancel={() => {
                    setCreateModalVisible(false);
                    form.resetFields();
                }}
                footer={null}
                width={600}
            >
                <Form
                    form={form}
                    layout="vertical"
                    onFinish={handleCreate}
                >
                    <Form.Item
                        label="用户名"
                        name="username"
                        rules={[{ required: true, message: '请输入用户名' }]}
                    >
                        <Input placeholder="请输入用户名" />
                    </Form.Item>

                    <Form.Item
                        label="密码"
                        name="password"
                        rules={[
                            { required: true, message: '请输入密码' },
                            { min: 6, max: 20, message: '密码长度必须在6-20位之间' }
                        ]}
                    >
                        <Input.Password placeholder="请输入密码（6-20位）" />
                    </Form.Item>

                    {activeTab === 'staff' && (
                        <Form.Item
                            label="角色"
                            name="role"
                            rules={[{ required: true, message: '请选择角色' }]}
                        >
                            <Select placeholder="请选择角色">
                                <Select.Option value="ADMIN">管理员</Select.Option>
                                <Select.Option value="AGENT">客服</Select.Option>
                            </Select>
                        </Form.Item>
                    )}

                    <Form.Item label="昵称" name="nickName">
                        <Input placeholder="可选" />
                    </Form.Item>

                    <Form.Item
                        label="邮箱"
                        name="email"
                        rules={[{ type: 'email', message: '请输入有效的邮箱地址' }]}
                    >
                        <Input placeholder="可选" />
                    </Form.Item>

                    <Form.Item
                        label="手机号"
                        name="phone"
                    >
                        <Input placeholder="可选" />
                    </Form.Item>

                    <Form.Item>
                        <Space>
                            <Button type="primary" htmlType="submit">
                                创建
                            </Button>
                            <Button onClick={() => {
                                setCreateModalVisible(false);
                                form.resetFields();
                            }}>
                                取消
                            </Button>
                        </Space>
                    </Form.Item>
                </Form>
            </Modal>

            {/* 编辑用户弹窗 */}
            <Modal
                title="编辑用户"
                open={editModalVisible}
                onCancel={() => {
                    setEditModalVisible(false);
                    setSelectedUser(null);
                    form.resetFields();
                }}
                footer={null}
                width={600}
            >
                <Form
                    form={form}
                    layout="vertical"
                    onFinish={handleEdit}
                >
                    {activeTab === 'staff' && (
                        <Form.Item
                            label="角色"
                            name="role"
                            rules={[{ required: true, message: '请选择角色' }]}
                        >
                            <Select placeholder="请选择角色">
                                <Select.Option value="ADMIN">管理员</Select.Option>
                                <Select.Option value="AGENT">客服</Select.Option>
                            </Select>
                        </Form.Item>
                    )}

                    <Form.Item label="昵称" name="nickName">
                        <Input placeholder="可选" />
                    </Form.Item>

                    <Form.Item
                        label="邮箱"
                        name="email"
                        rules={[{ type: 'email', message: '请输入有效的邮箱地址' }]}
                    >
                        <Input placeholder="可选" />
                    </Form.Item>

                    <Form.Item
                        label="手机号"
                        name="phone"
                    >
                        <Input placeholder="可选" />
                    </Form.Item>

                    <Form.Item
                        label="状态"
                        name="status"
                        valuePropName="checked"
                        getValueFromEvent={(checked) => checked ? 1 : 0}
                    >
                        <Switch checkedChildren="启用" unCheckedChildren="禁用" />
                    </Form.Item>

                    <Form.Item>
                        <Space>
                            <Button type="primary" htmlType="submit">
                                保存
                            </Button>
                            <Button onClick={() => {
                                setEditModalVisible(false);
                                setSelectedUser(null);
                                form.resetFields();
                            }}>
                                取消
                            </Button>
                        </Space>
                    </Form.Item>
                </Form>
            </Modal>

            {/* 重置密码弹窗 */}
            <Modal
                title="重置密码"
                open={resetPasswordModalVisible}
                onCancel={() => {
                    setResetPasswordModalVisible(false);
                    setSelectedUser(null);
                    passwordForm.resetFields();
                }}
                footer={null}
                width={500}
            >
                <Form
                    form={passwordForm}
                    layout="vertical"
                    onFinish={handleResetPassword}
                >
                    <Form.Item
                        label="新密码"
                        name="newPassword"
                        rules={[
                            { required: true, message: '请输入新密码' },
                            { min: 6, max: 20, message: '密码长度必须在6-20位之间' }
                        ]}
                    >
                        <Input.Password placeholder="请输入新密码（6-20位）" />
                    </Form.Item>

                    <Form.Item
                        label="确认密码"
                        name="confirmPassword"
                        dependencies={['newPassword']}
                        rules={[
                            { required: true, message: '请确认密码' },
                            ({ getFieldValue }) => ({
                                validator(_, value) {
                                    if (!value || getFieldValue('newPassword') === value) {
                                        return Promise.resolve();
                                    }
                                    return Promise.reject(new Error('两次输入的密码不一致'));
                                }
                            })
                        ]}
                    >
                        <Input.Password placeholder="请再次输入密码" />
                    </Form.Item>

                    <Form.Item>
                        <Space>
                            <Button type="primary" htmlType="submit">
                                确定
                            </Button>
                            <Button onClick={() => {
                                setResetPasswordModalVisible(false);
                                setSelectedUser(null);
                                passwordForm.resetFields();
                            }}>
                                取消
                            </Button>
                        </Space>
                    </Form.Item>
                </Form>
            </Modal>
        </Card>
    );
}