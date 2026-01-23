import { useState, useEffect } from 'react';
import {
  Card,
  Table,
  Button,
  Upload,
  message,
  Space,
  Tag,
  Popconfirm,
  Modal,
  Form,
  Input
} from 'antd';
import {
  UploadOutlined,
  DeleteOutlined,
  ReloadOutlined
} from '@ant-design/icons';
import type { UploadFile, UploadProps } from 'antd';
import { knowledgeApi } from '../../api/chat';
import type { DocumentListItem, ApiResponse } from '../../api/types';

const { TextArea } = Input;

export default function KnowledgePage() {
  const [documents, setDocuments] = useState<DocumentListItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [uploadModalVisible, setUploadModalVisible] = useState(false);
  const [indexingDocId, setIndexingDocId] = useState<number | null>(null);
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [form] = Form.useForm();

  useEffect(() => {
    loadDocuments();
  }, []);

  const loadDocuments = async () => {
    setLoading(true);
    try {
      const res = await knowledgeApi.listDocuments() as any as ApiResponse<DocumentListItem[]>;
      if (res.success || res.code === '200') {
        setDocuments(res.data || []);
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '加载文档列表失败');
    } finally {
      setLoading(false);
    }
  };

  const handleUpload = async (values: { title?: string; source?: string }) => {
    if (!fileList || fileList.length === 0) {
      message.warning('请选择要上传的文件');
      return;
    }

    const file = fileList[0].originFileObj;
    if (!file) {
      message.warning('文件无效');
      return;
    }

    setUploading(true);
    try {
      const res = await knowledgeApi.uploadDocument(
        file,
        values.title,
        values.source
      ) as any as ApiResponse<number>;
      if (res.success || res.code === '200') {
        message.success('文档上传成功');
        setUploadModalVisible(false);
        form.resetFields();
        setFileList([]); // 清空文件列表
        await loadDocuments();
        
        // 自动开始索引
        if (res.data) {
          await handleIndex(res.data);
        }
      } else {
        // 如果返回了错误响应但没有抛出异常
        message.error(res.message || '上传失败');
      }
    } catch (e: any) {
      // 处理各种错误情况
      let errorMessage = '上传失败';
      if (e?.response?.data) {
        // 后端返回的错误信息
        errorMessage = e.response.data.message || e.response.data.error || errorMessage;
      } else if (e?.message) {
        // 网络错误或其他错误
        errorMessage = e.message;
      }
      message.error(errorMessage);
    } finally {
      setUploading(false);
    }
  };

  const handleIndex = async (documentId: number) => {
    setIndexingDocId(documentId);
    try {
      const res = await knowledgeApi.indexDocument(documentId) as any as ApiResponse<void>;
      if (res.success || res.code === '200') {
        message.success('文档索引成功');
        await loadDocuments();
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '索引失败');
    } finally {
      setIndexingDocId(null);
    }
  };

  const handleDelete = async (documentId: number) => {
    try {
      const res = await knowledgeApi.deleteDocument(documentId) as any as ApiResponse<void>;
      if (res.success || res.code === '200') {
        message.success('文档删除成功');
        await loadDocuments();
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '删除失败');
    }
  };

  const uploadProps: UploadProps = {
    beforeUpload: () => false, // 阻止自动上传
    maxCount: 1,
    accept: '.txt,.pdf,.doc,.docx,.md',
    fileList: fileList,
    onChange: ({ fileList: newFileList }) => {
      setFileList(newFileList);
    },
    onRemove: () => {
      setFileList([]);
    }
  };

  const columns = [
    {
      title: '文档标题',
      dataIndex: 'title',
      key: 'title',
      render: (text: string) => text || '未命名文档'
    },
    {
      title: '来源',
      dataIndex: 'source',
      key: 'source',
      render: (text: string) => text || '-'
    },
    {
      title: '文件类型',
      dataIndex: 'fileType',
      key: 'fileType',
      render: (text: string) => <Tag>{text || '-'}</Tag>
    },
    {
      title: '文件大小',
      dataIndex: 'fileSize',
      key: 'fileSize',
      render: (size: number) => {
        if (!size) return '-';
        if (size < 1024) return `${size} B`;
        if (size < 1024 * 1024) return `${(size / 1024).toFixed(2)} KB`;
        return `${(size / (1024 * 1024)).toFixed(2)} MB`;
      }
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: number) => {
        if (status === 1) {
          return <Tag color="green">已索引</Tag>;
        }
        return <Tag color="orange">未索引</Tag>;
      }
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      render: (time: string) => {
        if (!time) return '-';
        return new Date(time).toLocaleString('zh-CN');
      }
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: DocumentListItem) => (
        <Space>
          {record.status === 0 && (
            <Button
              type="link"
              icon={<ReloadOutlined />}
              onClick={() => handleIndex(record.id)}
              loading={indexingDocId === record.id}
            >
              索引
            </Button>
          )}
          <Popconfirm
            title="确定删除此文档吗？"
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

  return (
    <div>
      <Card
        title="知识库管理"
        extra={
          <Button
            type="primary"
            icon={<UploadOutlined />}
            onClick={() => setUploadModalVisible(true)}
          >
            上传文档
          </Button>
        }
      >
        <Table
          columns={columns}
          dataSource={documents}
          rowKey="id"
          loading={loading}
          pagination={{
            pageSize: 10,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`
          }}
        />
      </Card>

      {/* 上传文档弹窗 */}
      <Modal
        title="上传文档"
        open={uploadModalVisible}
        onCancel={() => {
          setUploadModalVisible(false);
          form.resetFields();
          setFileList([]); // 清空文件列表
        }}
        footer={null}
        width={600}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleUpload}
        >
          <Form.Item
            label="选择文件"
            required
            rules={[
              {
                validator: () => {
                  if (fileList.length === 0) {
                    return Promise.reject(new Error('请选择要上传的文件'));
                  }
                  return Promise.resolve();
                }
              }
            ]}
          >
            <Upload {...uploadProps}>
              <Button icon={<UploadOutlined />}>选择文件</Button>
            </Upload>
            <div style={{ marginTop: 8, fontSize: 12, color: '#999' }}>
              支持格式：.txt, .pdf, .doc, .docx, .md
            </div>
          </Form.Item>

          <Form.Item 
            label="文档标题" 
            name="title"
            rules={[
              { required: true, message: '请输入文档标题' },
              { whitespace: true, message: '文档标题不能为空' }
            ]}
          >
            <Input placeholder="请输入文档标题" />
          </Form.Item>

          <Form.Item label="来源（可选）" name="source">
            <Input placeholder="例如：内部文档、产品手册等" />
          </Form.Item>

          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" loading={uploading}>
                上传
              </Button>
              <Button onClick={() => {
                setUploadModalVisible(false);
                form.resetFields();
                setFileList([]); // 清空文件列表
              }}>
                取消
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

