import { useEffect, useMemo, useState } from 'react';
import {
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tabs,
  message
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { adminFaqApi } from '../../api/faq';
import type { FaqCategory, FaqItem } from '../../api/types';

export default function AdminFaqPage() {
  const [categories, setCategories] = useState<FaqCategory[]>([]);
  const [items, setItems] = useState<FaqItem[]>([]);
  const [loadingCategory, setLoadingCategory] = useState(false);
  const [loadingItem, setLoadingItem] = useState(false);
  const [itemKeyword, setItemKeyword] = useState('');
  const [itemCategoryId, setItemCategoryId] = useState<number | undefined>(undefined);
  const [categoryModalOpen, setCategoryModalOpen] = useState(false);
  const [itemModalOpen, setItemModalOpen] = useState(false);
  const [editingCategory, setEditingCategory] = useState<FaqCategory | null>(null);
  const [editingItem, setEditingItem] = useState<FaqItem | null>(null);
  const [categoryForm] = Form.useForm();
  const [itemForm] = Form.useForm();

  useEffect(() => {
    loadCategories();
    loadItems();
  }, []);

  const categoryMap = useMemo(() => {
    const map = new Map<number, string>();
    categories.forEach((c) => map.set(c.id, c.name));
    return map;
  }, [categories]);

  const loadCategories = async () => {
    setLoadingCategory(true);
    try {
      const res = await adminFaqApi.categories();
      if (res.success || res.code === '200') {
        setCategories(res.data || []);
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '加载FAQ分类失败');
    } finally {
      setLoadingCategory(false);
    }
  };

  const loadItems = async () => {
    setLoadingItem(true);
    try {
      const res = await adminFaqApi.listItems({
        categoryId: itemCategoryId,
        keyword: itemKeyword || undefined,
        page: 1,
        size: 100
      });
      if (res.success || res.code === '200') {
        setItems(res.data?.records || []);
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '加载FAQ条目失败');
    } finally {
      setLoadingItem(false);
    }
  };

  const onOpenCategoryCreate = () => {
    setEditingCategory(null);
    categoryForm.setFieldsValue({ name: '', sort: 100, status: 1 });
    setCategoryModalOpen(true);
  };

  const onOpenCategoryEdit = (record: FaqCategory) => {
    setEditingCategory(record);
    categoryForm.setFieldsValue({
      name: record.name,
      sort: record.sort ?? 100,
      status: 1
    });
    setCategoryModalOpen(true);
  };

  const saveCategory = async () => {
    const values = await categoryForm.validateFields();
    try {
      if (editingCategory) {
        await adminFaqApi.updateCategory(editingCategory.id, values);
      } else {
        await adminFaqApi.createCategory(values);
      }
      message.success('分类保存成功');
      setCategoryModalOpen(false);
      loadCategories();
    } catch (e: any) {
      message.error(e?.response?.data?.message || '分类保存失败');
    }
  };

  const onDeleteCategory = async (id: number) => {
    try {
      await adminFaqApi.deleteCategory(id);
      message.success('分类删除成功');
      loadCategories();
    } catch (e: any) {
      message.error(e?.response?.data?.message || '分类删除失败');
    }
  };

  const onOpenItemCreate = () => {
    setEditingItem(null);
    itemForm.setFieldsValue({ categoryId: undefined, question: '', answer: '', keywords: '', sort: 100, status: 1 });
    setItemModalOpen(true);
  };

  const onOpenItemEdit = (record: FaqItem) => {
    setEditingItem(record);
    itemForm.setFieldsValue({
      categoryId: record.categoryId,
      question: record.question,
      answer: record.answer,
      keywords: record.keywords,
      sort: record.sort ?? 100,
      status: record.status ?? 1
    });
    setItemModalOpen(true);
  };

  const saveItem = async () => {
    const values = await itemForm.validateFields();
    try {
      if (editingItem) {
        await adminFaqApi.updateItem(editingItem.id, values);
      } else {
        await adminFaqApi.createItem(values);
      }
      message.success('FAQ保存成功');
      setItemModalOpen(false);
      loadItems();
    } catch (e: any) {
      message.error(e?.response?.data?.message || 'FAQ保存失败');
    }
  };

  const onDeleteItem = async (id: number) => {
    try {
      await adminFaqApi.deleteItem(id);
      message.success('FAQ删除成功');
      loadItems();
    } catch (e: any) {
      message.error(e?.response?.data?.message || 'FAQ删除失败');
    }
  };

  const categoryColumns: ColumnsType<FaqCategory> = [
    { title: 'ID', dataIndex: 'id', width: 90 },
    { title: '分类名称', dataIndex: 'name' },
    { title: '排序', dataIndex: 'sort', width: 100 },
    {
      title: '操作',
      key: 'action',
      width: 180,
      render: (_, record) => (
        <Space>
          <Button size="small" onClick={() => onOpenCategoryEdit(record)}>
            编辑
          </Button>
          <Popconfirm title="确认删除该分类吗？" onConfirm={() => onDeleteCategory(record.id)}>
            <Button size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      )
    }
  ];

  const itemColumns: ColumnsType<FaqItem> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '问题', dataIndex: 'question' },
    {
      title: '分类',
      dataIndex: 'categoryId',
      width: 140,
      render: (v) => categoryMap.get(v) || `分类#${v}`
    },
    {
      title: '反馈',
      key: 'feedback',
      width: 140,
      render: (_, record) => `👍 ${record.helpfulCount ?? 0} / 👎 ${record.unhelpfulCount ?? 0}`
    },
    {
      title: '状态',
      key: 'status',
      width: 120,
      render: (_, record) => <Tag color={record.status === 0 ? 'default' : 'green'}>{record.status === 0 ? '已下架' : '已上架'}</Tag>
    },
    {
      title: '操作',
      key: 'action',
      width: 260,
      render: (_, record) => (
        <Space>
          <Button size="small" onClick={() => onOpenItemEdit(record)}>
            编辑
          </Button>
          <Switch
            size="small"
            checked={record.status !== 0}
            checkedChildren="上架"
            unCheckedChildren="下架"
            onChange={async (checked) => {
              await adminFaqApi.updateItemStatus(record.id, checked ? 1 : 0);
              loadItems();
            }}
          />
          <Popconfirm title="确认删除该FAQ吗？" onConfirm={() => onDeleteItem(record.id)}>
            <Button size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      )
    }
  ];

  return (
    <>
      <Tabs
        items={[
          {
            key: 'category',
            label: '分类管理',
            children: (
              <Card
                extra={
                  <Space>
                    <Button onClick={loadCategories}>刷新</Button>
                    <Button type="primary" onClick={onOpenCategoryCreate}>
                      新建分类
                    </Button>
                  </Space>
                }
              >
                <Table rowKey="id" loading={loadingCategory} columns={categoryColumns} dataSource={categories} pagination={false} />
              </Card>
            )
          },
          {
            key: 'item',
            label: 'FAQ管理',
            children: (
              <Card
                extra={
                  <Space>
                    <Select
                      allowClear
                      placeholder="分类筛选"
                      style={{ width: 160 }}
                      value={itemCategoryId}
                      options={categories.map((c) => ({ value: c.id, label: c.name }))}
                      onChange={(v) => setItemCategoryId(v)}
                    />
                    <Input.Search
                      placeholder="关键词搜索"
                      style={{ width: 220 }}
                      allowClear
                      value={itemKeyword}
                      onChange={(e) => setItemKeyword(e.target.value)}
                      onSearch={loadItems}
                    />
                    <Button onClick={loadItems}>刷新</Button>
                    <Button type="primary" onClick={onOpenItemCreate}>
                      新建FAQ
                    </Button>
                  </Space>
                }
              >
                <Table rowKey="id" loading={loadingItem} columns={itemColumns} dataSource={items} />
              </Card>
            )
          }
        ]}
      />

      <Modal
        title={editingCategory ? '编辑分类' : '新建分类'}
        open={categoryModalOpen}
        onCancel={() => setCategoryModalOpen(false)}
        onOk={saveCategory}
      >
        <Form form={categoryForm} layout="vertical">
          <Form.Item label="分类名称" name="name" rules={[{ required: true, message: '请输入分类名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="排序" name="sort">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={editingItem ? '编辑FAQ' : '新建FAQ'}
        open={itemModalOpen}
        onCancel={() => setItemModalOpen(false)}
        onOk={saveItem}
        width={760}
      >
        <Form form={itemForm} layout="vertical">
          <Form.Item name="status" hidden>
            <InputNumber />
          </Form.Item>
          <Form.Item label="分类" name="categoryId" rules={[{ required: true, message: '请选择分类' }]}>
            <Select options={categories.map((c) => ({ value: c.id, label: c.name }))} />
          </Form.Item>
          <Form.Item label="问题" name="question" rules={[{ required: true, message: '请输入问题' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="答案" name="answer" rules={[{ required: true, message: '请输入答案' }]}>
            <Input.TextArea rows={5} />
          </Form.Item>
          <Form.Item label="关键词" name="keywords">
            <Input placeholder="例如：退款,发票,售后" />
          </Form.Item>
          <Form.Item label="排序" name="sort">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
