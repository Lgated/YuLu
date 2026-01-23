import { useState, useEffect } from 'react';
import { Card, Table, Tag, Button, Space, message, Modal, Descriptions } from 'antd';
import { EyeOutlined } from '@ant-design/icons';
import { knowledgeApi } from '../../api/chat';
import type { DocumentListItem, DocumentDetail, ApiResponse } from '../../api/types';

/**
 * 客服端知识库查询页面（只读模式）
 * 复用管理员的知识库页面，但禁用所有编辑功能
 */
export default function AgentKnowledgePage() {
  const [documents, setDocuments] = useState<DocumentListItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [detailModalVisible, setDetailModalVisible] = useState(false);
  const [selectedDocument, setSelectedDocument] = useState<DocumentDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0
  });

  // 加载文档列表
  const loadDocuments = async (page = 1, pageSize = 10) => {
    setLoading(true);
    try {
      const res = await knowledgeApi.listDocuments(page, pageSize) as any as ApiResponse<DocumentListItem[]>;
      
      if (res.success || res.code === '200') {
        const docs = res.data || [];
        setDocuments(docs);
        // 如果后端返回的是分页对象，需要调整
        // 这里假设返回的是数组，前端做分页
        setPagination({
          current: page,
          pageSize: pageSize,
          total: docs.length
        });
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '加载文档列表失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadDocuments();
  }, []);

  // 查看文档详情
  const handleViewDetail = async (documentId: number) => {
    setDetailLoading(true);
    setDetailModalVisible(true);
    try {
      const res = await knowledgeApi.getDocumentDetail(documentId) as any as ApiResponse<DocumentDetail>;
      if (res.success || res.code === '200') {
        setSelectedDocument(res.data);
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '加载文档详情失败');
      setDetailModalVisible(false);
    } finally {
      setDetailLoading(false);
    }
  };

  // 获取状态标签
  const getStatusTag = (status: number) => {
    if (status === 1) {
      return <Tag color="green">已索引</Tag>;
    } else if (status === 0) {
      return <Tag color="orange">未索引</Tag>;
    }
    return <Tag>未知</Tag>;
  };

  // 表格列定义
  const columns = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 80
    },
    {
      title: '文档标题',
      dataIndex: 'title',
      key: 'title',
      ellipsis: true
    },
    {
      title: '来源',
      dataIndex: 'source',
      key: 'source',
      width: 150,
      ellipsis: true
    },
    {
      title: '文件类型',
      dataIndex: 'fileType',
      key: 'fileType',
      width: 100,
      render: (fileType: string) => fileType ? <Tag>{fileType}</Tag> : '-'
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: number) => getStatusTag(status)
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
      width: 100,
      render: (_: any, record: DocumentListItem) => (
        <Space>
          <Button
            type="link"
            icon={<EyeOutlined />}
            onClick={() => handleViewDetail(record.id)}
          >
            查看
          </Button>
        </Space>
      )
    }
  ];

  return (
    <Card title="知识库查询">
      <div style={{ marginBottom: 16 }}>
        <span style={{ color: '#999', fontSize: 12 }}>
          💡 提示：此页面为只读模式，仅可查看知识库文档，无法进行上传、删除、索引等操作。
        </span>
      </div>
      
      <Table
        columns={columns}
        dataSource={documents}
        rowKey="id"
        loading={loading}
        pagination={{
          current: pagination.current,
          pageSize: pagination.pageSize,
          total: pagination.total,
          onChange: (page, pageSize) => {
            setPagination({ ...pagination, current: page, pageSize });
            loadDocuments(page, pageSize);
          },
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 条`
        }}
      />

      {/* 文档详情弹窗 */}
      <Modal
        title="文档详情"
        open={detailModalVisible}
        onCancel={() => {
          setDetailModalVisible(false);
          setSelectedDocument(null);
        }}
        footer={[
          <Button key="close" onClick={() => {
            setDetailModalVisible(false);
            setSelectedDocument(null);
          }}>
            关闭
          </Button>
        ]}
        width={800}
      >
        {detailLoading ? (
          <div style={{ textAlign: 'center', padding: 40 }}>
            加载中...
          </div>
        ) : selectedDocument ? (
          <Descriptions column={1} bordered>
            <Descriptions.Item label="文档ID">{selectedDocument.id}</Descriptions.Item>
            <Descriptions.Item label="文档标题">{selectedDocument.title}</Descriptions.Item>
            <Descriptions.Item label="来源">{selectedDocument.source || '-'}</Descriptions.Item>
            <Descriptions.Item label="文件类型">{selectedDocument.fileType || '-'}</Descriptions.Item>
            <Descriptions.Item label="状态">{getStatusTag(selectedDocument.status)}</Descriptions.Item>
            <Descriptions.Item label="创建时间">{selectedDocument.createTime}</Descriptions.Item>
            {selectedDocument.indexedAt && (
              <Descriptions.Item label="索引时间">{selectedDocument.indexedAt}</Descriptions.Item>
            )}
            {selectedDocument.contentPreview && (
              <Descriptions.Item label="文档内容预览">
                <div style={{ 
                  maxHeight: 300, 
                  overflow: 'auto', 
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word'
                }}>
                  {selectedDocument.contentPreview}
                </div>
              </Descriptions.Item>
            )}
          </Descriptions>
        ) : null}
      </Modal>
    </Card>
  );
}

