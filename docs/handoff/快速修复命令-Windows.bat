@echo off
REM ============================================
REM 转人工功能快速修复脚本 (Windows)
REM ============================================
REM 使用说明：
REM 1. 替换 YOUR_TOKEN 为实际的管理员 JWT token
REM 2. 确认后端地址（默认 localhost:8080）
REM 3. 双击运行此脚本
REM ============================================

SET BASE_URL=http://localhost:8080
SET TOKEN=YOUR_TOKEN
SET AGENT_ID=3

echo ==========================================
echo 步骤 1: 检查客服 Redis 数据结构
echo ==========================================
curl -X GET "%BASE_URL%/api/admin/debug/check-agent-redis/%AGENT_ID%" ^
  -H "Authorization: Bearer %TOKEN%" ^
  -H "Content-Type: application/json"

echo.
echo ==========================================
echo 步骤 2: 强制重置客服状态
echo ==========================================
curl -X POST "%BASE_URL%/api/admin/debug/force-reset-agent/%AGENT_ID%" ^
  -H "Authorization: Bearer %TOKEN%" ^
  -H "Content-Type: application/json"

echo.
echo ==========================================
echo 步骤 3: 清空转人工队列
echo ==========================================
curl -X DELETE "%BASE_URL%/api/admin/debug/clear-queue" ^
  -H "Authorization: Bearer %TOKEN%" ^
  -H "Content-Type: application/json"

echo.
echo ==========================================
echo 步骤 4: 再次检查 Redis 数据结构
echo ==========================================
curl -X GET "%BASE_URL%/api/admin/debug/check-agent-redis/%AGENT_ID%" ^
  -H "Authorization: Bearer %TOKEN%" ^
  -H "Content-Type: application/json"

echo.
echo ==========================================
echo ✅ Redis 清理完成！
echo ==========================================
echo.
echo 接下来请执行：
echo 1. 客服刷新页面
echo 2. 客服点击【上线】按钮
echo 3. 再次运行此脚本检查 Redis 数据结构
echo    - statusKeyType 应该是 'HASH'
echo    - statusData 应该包含 status, current_sessions 等字段
echo.
echo ==========================================
pause
