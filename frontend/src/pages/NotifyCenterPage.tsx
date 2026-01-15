import { useEffect, useState } from 'react';
import { Card, List, Tag } from 'antd';
import { notifyApi } from '../api/notify';
import type { NotifyMessage } from '../api/types';

export default function NotifyCenterPage() {
  const [data, setData] = useState<NotifyMessage[]>([]);

  useEffect(() => {
    load();
  }, []);

  const load = async () => {
    try {
      const res = await notifyApi.list({ page: 1, size: 50 });
      if (res.success || res.code === '200') {
        setData(res.data?.records || []);
      }
    } catch (e: any) {
      console.error('加载通知失败', e);
    }
  };

  return (
    <Card title="通知中心">
      <List
        dataSource={data}
        renderItem={(item) => (
          <List.Item>
            <List.Item.Meta
              title={
                <>
                  {item.title}{' '}
                  {item.readFlag === 0 && (
                    <Tag color="blue" style={{ marginLeft: 8 }}>
                      未读
                    </Tag>
                  )}
                </>
              }
              description={item.content}
            />
            <div style={{ fontSize: 12, color: '#999' }}>{item.createTime}</div>
          </List.Item>
        )}
      />
    </Card>
  );
}



