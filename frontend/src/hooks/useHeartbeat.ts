import { useEffect, useRef } from 'react';
import { agentApi } from '../api/agent';
import { getRole } from '../utils/storage';

/**
 * 心跳定时器 Hook
 * 用于客服保持在线状态
 * 
 * 使用方式：
 * ```tsx
 * useHeartbeat({ enabled: true, interval: 30000 });
 * ```
 */
export function useHeartbeat(options: {
  enabled?: boolean;
  interval?: number; // 心跳间隔（毫秒），默认30秒
}) {
  const { enabled = true, interval = 30000 } = options;
  const intervalRef = useRef<NodeJS.Timeout | null>(null);

  useEffect(() => {
    // 只有客服角色才需要心跳
    const role = getRole();
    if (!enabled || role !== 'AGENT') {
      return;
    }

    // 立即发送一次心跳
    const sendHeartbeat = async () => {
      try {
        await agentApi.heartbeat();
      } catch (error) {
        // 心跳失败不显示错误，避免打扰用户
        console.warn('心跳发送失败:', error);
      }
    };

    // 立即发送一次
    sendHeartbeat();

    // 设置定时器
    intervalRef.current = setInterval(() => {
      sendHeartbeat();
    }, interval);

    // 清理函数
    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };
  }, [enabled, interval]);

  // 返回清理函数（可选，用于手动停止）
  return () => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
  };
}






















