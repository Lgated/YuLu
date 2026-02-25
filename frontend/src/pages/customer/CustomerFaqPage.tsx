import { useEffect, useMemo, useState } from 'react';
import { Button, Card, Col, Collapse, Input, List, Row, Select, Space, Tag, message } from 'antd';
import { customerFaqApi } from '../../api/faq';
import type { FaqCategory, FaqItem } from '../../api/types';

export default function CustomerFaqPage() {
  const [categories, setCategories] = useState<FaqCategory[]>([]);
  const [hotList, setHotList] = useState<FaqItem[]>([]);
  const [records, setRecords] = useState<FaqItem[]>([]);
  const [viewedIds, setViewedIds] = useState<Set<number>>(new Set());
  const [categoryId, setCategoryId] = useState<number | undefined>(undefined);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    loadBaseData();
    loadFaqList();
  }, []);

  const categoryMap = useMemo(() => {
    const map = new Map<number, string>();
    categories.forEach((c) => map.set(c.id, c.name));
    return map;
  }, [categories]);

  const loadBaseData = async () => {
    try {
      const [cRes, hRes] = await Promise.all([customerFaqApi.categories(), customerFaqApi.hot(8)]);
      if (cRes.success || cRes.code === '200') {
        setCategories(cRes.data || []);
      }
      if (hRes.success || hRes.code === '200') {
        setHotList(hRes.data || []);
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '加载FAQ基础数据失败');
    }
  };

  const loadFaqList = async (next?: { categoryId?: number; keyword?: string }) => {
    setLoading(true);
    try {
      const res = await customerFaqApi.list({
        categoryId: next?.categoryId ?? categoryId,
        keyword: next?.keyword ?? keyword,
        page: 1,
        size: 50
      });
      if (res.success || res.code === '200') {
        setRecords(res.data?.records || []);
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '加载FAQ列表失败');
    } finally {
      setLoading(false);
    }
  };

  const onFeedback = async (faqId: number, feedbackType: 1 | 0) => {
    try {
      const res = await customerFaqApi.feedback({ faqId, feedbackType });
      if (res.success || res.code === '200') {
        message.success('反馈成功');
        loadFaqList();
        loadBaseData();
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '反馈失败');
    }
  };

  const onExpandAndView = async (keys: string | string[]) => {
    const arr = Array.isArray(keys) ? keys : [keys];
    const last = arr[arr.length - 1];
    if (!last) return;
    const faqId = Number(last);
    if (!faqId || viewedIds.has(faqId)) return;

    try {
      const res = await customerFaqApi.view(faqId);
      if (res.success || res.code === '200') {
        setViewedIds((prev) => {
          const next = new Set(prev);
          next.add(faqId);
          return next;
        });
        setRecords((prev) =>
          prev.map((item) =>
            item.id === faqId ? { ...item, viewCount: (item.viewCount || 0) + 1 } : item
          )
        );
        setHotList((prev) =>
          prev.map((item) =>
            item.id === faqId ? { ...item, viewCount: (item.viewCount || 0) + 1 } : item
          )
        );
      }
    } catch {
      // view 埋点失败不影响用户继续阅读
    }
  };

  return (
    <Row gutter={16}>
      <Col xs={24} lg={17}>
        <Card
          title="常见问题"
          extra={
            <Space>
              <Select
                allowClear
                placeholder="按分类筛选"
                style={{ width: 180 }}
                value={categoryId}
                onChange={(v) => {
                  setCategoryId(v);
                  loadFaqList({ categoryId: v });
                }}
                options={categories.map((c) => ({ value: c.id, label: c.name }))}
              />
              <Input.Search
                placeholder="输入关键词搜索"
                allowClear
                style={{ width: 260 }}
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                onSearch={(v) => {
                  setKeyword(v);
                  loadFaqList({ keyword: v });
                }}
              />
            </Space>
          }
          loading={loading}
        >
          <Collapse
            onChange={onExpandAndView}
            items={records.map((item) => ({
              key: String(item.id),
              label: (
                <Space>
                  <span>{item.question}</span>
                  <Tag>{categoryMap.get(item.categoryId) || `分类#${item.categoryId}`}</Tag>
                </Space>
              ),
              children: (
                <div>
                  <p style={{ marginBottom: 12, whiteSpace: 'pre-wrap' }}>{item.answer}</p>
                  <Space>
                    <Button size="small" onClick={() => onFeedback(item.id, 1)}>
                      有帮助 ({item.helpfulCount ?? 0})
                    </Button>
                    <Button size="small" onClick={() => onFeedback(item.id, 0)}>
                      没帮助 ({item.unhelpfulCount ?? 0})
                    </Button>
                  </Space>
                </div>
              )
            }))}
          />
        </Card>
      </Col>
      <Col xs={24} lg={7}>
        <Card title="热门问题">
          <List
            dataSource={hotList}
            renderItem={(item, idx) => (
              <List.Item>
                <List.Item.Meta
                  title={`${idx + 1}. ${item.question}`}
                  description={`👍 ${item.helpfulCount ?? 0} · 浏览 ${item.viewCount ?? 0}`}
                />
              </List.Item>
            )}
          />
        </Card>
      </Col>
    </Row>
  );
}
