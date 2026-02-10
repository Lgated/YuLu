#!/bin/bash

# ============================================
# 转人工功能快速修复脚本
# ============================================
# 使用说明：
# 1. 替换 YOUR_TOKEN 为实际的管理员 JWT token
# 2. 确认后端地址（默认 localhost:8080）
# 3. 按顺序执行每个步骤
# ============================================

# 配置
BASE_URL="http://localhost:8080"
TOKEN="YOUR_TOKEN"  # 替换为实际的 token
AGENT_ID=3          # 客服用户ID

echo "=========================================="
echo "步骤 1: 检查客服 Redis 数据结构"
echo "=========================================="
curl -X GET "${BASE_URL}/api/admin/debug/check-agent-redis/${AGENT_ID}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq .

echo ""
echo "=========================================="
echo "步骤 2: 强制重置客服状态（删除损坏的 Redis key）"
echo "=========================================="
curl -X POST "${BASE_URL}/api/admin/debug/force-reset-agent/${AGENT_ID}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq .

echo ""
echo "=========================================="
echo "步骤 3: 清空转人工队列"
echo "=========================================="
curl -X DELETE "${BASE_URL}/api/admin/debug/clear-queue" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq .

echo ""
echo "=========================================="
echo "步骤 4: 再次检查 Redis 数据结构（应该显示 key 不存在）"
echo "=========================================="
curl -X GET "${BASE_URL}/api/admin/debug/check-agent-redis/${AGENT_ID}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" | jq .

echo ""
echo "=========================================="
echo "✅ Redis 清理完成！"
echo "=========================================="
echo ""
echo "接下来请执行："
echo "1. 客服刷新页面"
echo "2. 客服点击【上线】按钮"
echo "3. 再次运行步骤 1 检查 Redis 数据结构"
echo "   - statusKeyType 应该是 'HASH'"
echo "   - statusData 应该包含 status, current_sessions 等字段"
echo ""
echo "=========================================="
