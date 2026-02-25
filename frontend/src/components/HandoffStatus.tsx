import { Alert, Button, Space, Spin } from 'antd';
import type { HandoffStatusResponse } from '../api/types';

interface HandoffStatusProps {
  statusInfo: HandoffStatusResponse;
  onCancel: () => void;
}

export default function HandoffStatus({ statusInfo, onCancel }: HandoffStatusProps) {
  const { status, queuePosition, estimatedWaitTime, assignedAgentName } = statusInfo;

  let message: React.ReactNode;
  let description: React.ReactNode | undefined;
  let showCancel = false;

  switch (status) {
    case 'PENDING':
    case 'ASSIGNED':
      message = '正在为您连接人工客服...';
      description = (
        <Space>
          <Spin size="small" />
          {queuePosition && queuePosition > 0
            ? `您当前排在第 ${queuePosition} 位，预计等待 ${estimatedWaitTime || '-'} 秒。`
            : '正在分配客服，请稍候。'}
        </Space>
      );
      showCancel = true;
      break;
    case 'ACCEPTED':
    case 'IN_PROGRESS':
      message = `客服 ${assignedAgentName || ''} 正在为您服务`;
      break;
    case 'COMPLETED':
      message = '本次人工服务已结束。';
      break;
    case 'CANCELLED':
      message = '您已取消转人工请求。';
      break;
    default:
      return null;
  }

  return (
    <Alert
      message={message}
      description={description}
      type="info"
      showIcon
      style={{ marginBottom: 16 }}
      action={
        showCancel ? (
          <Button size="small" type="text" danger onClick={onCancel}>
            取消
          </Button>
        ) : null
      }
    />
  );
}
