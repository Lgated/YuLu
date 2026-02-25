import { Modal, Form, Input, Button, message } from 'antd';
import { useState } from 'react';
import { handoffApi } from '../api/handoff';
import type { HandoffTransferResponse } from '../api/types';

interface HandoffModalProps {
  open: boolean;
  sessionId: number | null;
  onClose: () => void;
  onSuccess: (response: HandoffTransferResponse) => void;
}

export default function HandoffModal({ open, sessionId, onClose, onSuccess }: HandoffModalProps) {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  const handleFinish = async (values: { reason: string }) => {
    if (!sessionId) {
      message.error('会话 ID 无效');
      return;
    }
    setLoading(true);
    try {
      const res = await handoffApi.requestTransfer({ sessionId, reason: values.reason });
      if (res.success || res.code === '200') {
        onSuccess(res.data);
        form.resetFields();
      } else {
        message.error(res.message || '申请转人工失败');
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '申请转人工失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      title="申请转人工服务"
      open={open}
      onCancel={onClose}
      footer={null}
      destroyOnClose
    >
      <p style={{ marginBottom: 16, color: '#666' }}>
        您即将连接人工客服。如果需要，请简要描述您遇到的问题，以便我们更快地为您服务。
      </p>
      <Form form={form} onFinish={handleFinish} layout="vertical">
        <Form.Item name="reason" label="问题描述 (可选)">
          <Input.TextArea rows={4} placeholder="例如：AI 的回答不准确，我需要更详细的解释。" />
        </Form.Item>
        <Form.Item>
          <Button type="primary" htmlType="submit" loading={loading} block>
            确认转接
          </Button>
        </Form.Item>
      </Form>
    </Modal>
  );
}
