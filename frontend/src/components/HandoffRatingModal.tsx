import { Button, Form, Input, Modal, Rate, Space, Tag, message } from 'antd';
import { useMemo, useState } from 'react';
import { handoffApi } from '../api/handoff';

const { TextArea } = Input;

const TAG_OPTIONS = ['响应及时', '专业清晰', '态度友好', '问题已解决', '等待较久', '未解决'];

interface Props {
  open: boolean;
  handoffRequestId?: number;
  onClose: () => void;
  onSuccess?: () => void;
}

interface FormValues {
  score: number;
  tags?: string[];
  comment?: string;
}

export default function HandoffRatingModal({ open, handoffRequestId, onClose, onSuccess }: Props) {
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<FormValues>();

  const canSubmit = useMemo(() => !!handoffRequestId, [handoffRequestId]);

  const handleSubmit = async () => {
    if (!handoffRequestId) {
      message.error('缺少评价请求ID');
      return;
    }
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      await handoffApi.submitRating({
        handoffRequestId,
        score: values.score,
        tags: values.tags || [],
        comment: values.comment?.trim() || ''
      });
      message.success('感谢您的评价');
      form.resetFields();
      onClose();
      onSuccess?.();
    } catch (e: any) {
      if (e?.errorFields) return;
      message.error(e?.response?.data?.message || '提交评价失败');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      title="服务满意度评价"
      open={open}
      onCancel={onClose}
      footer={(
        <Space>
          <Button onClick={onClose}>稍后再评</Button>
          <Button type="primary" loading={submitting} disabled={!canSubmit} onClick={handleSubmit}>
            提交评价
          </Button>
        </Space>
      )}
      destroyOnClose
    >
      <Form form={form} layout="vertical" initialValues={{ score: 5, tags: [] }}>
        <Form.Item label="请为本次人工服务评分" name="score" rules={[{ required: true, message: '请先评分' }]}>
          <Rate />
        </Form.Item>

        <Form.Item label="标签（可选）" shouldUpdate>
          <Form.Item name="tags" noStyle>
            <input type="hidden" />
          </Form.Item>
          {() => {
            const selected = form.getFieldValue('tags') || [];
            return (
              <div>
                {TAG_OPTIONS.map((t) => {
                  const checked = selected.includes(t);
                  return (
                    <Tag.CheckableTag
                      key={t}
                      checked={checked}
                      onChange={(next) => {
                        const nextTags = next ? [...selected, t] : selected.filter((v: string) => v !== t);
                        form.setFieldValue('tags', nextTags);
                      }}
                    >
                      {t}
                    </Tag.CheckableTag>
                  );
                })}
              </div>
            );
          }}
        </Form.Item>

        <Form.Item label="补充说明（可选）" name="comment">
          <TextArea rows={4} maxLength={300} placeholder="请输入您的建议或遇到的问题" />
        </Form.Item>
      </Form>
    </Modal>
  );
}
