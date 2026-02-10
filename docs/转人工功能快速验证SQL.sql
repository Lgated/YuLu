-- 转人工功能快速验证 SQL

-- ============================================
-- 1. 检查会话状态
-- ============================================
-- 查询最近的转人工会话，检查 agent_id 是否正确设置
SELECT 
    cs.id AS session_id,
    cs.tenant_id,
    cs.user_id,
    cs.agent_id,  -- 应该有值（不为 null）
    cs.chat_mode,  -- 应该是 AGENT
    cs.handoff_request_id,
    cs.create_time,
    cs.update_time
FROM chat_session cs
WHERE cs.chat_mode = 'AGENT'
ORDER BY cs.create_time DESC
LIMIT 10;

-- ============================================
-- 2. 检查转人工请求状态
-- ============================================
-- 查询最近的转人工请求，检查状态流转
SELECT 
    hr.id AS handoff_request_id,
    hr.tenant_id,
    hr.session_id,
    hr.user_id,
    hr.agent_id,  -- 应该有值（分配后）
    hr.status,  -- PENDING -> ASSIGNED -> ACCEPTED -> COMPLETED
    hr.priority,
    hr.queue_position,
    hr.assigned_at,
    hr.accepted_at,
    hr.completed_at,
    hr.create_time
FROM handoff_request hr
ORDER BY hr.create_time DESC
LIMIT 10;

-- ============================================
-- 3. 检查会话和请求的关联关系
-- ============================================
-- 验证 chat_session 和 handoff_request 的 agent_id 是否一致
SELECT 
    cs.id AS session_id,
    cs.agent_id AS session_agent_id,
    hr.id AS handoff_request_id,
    hr.agent_id AS request_agent_id,
    hr.status,
    CASE 
        WHEN cs.agent_id = hr.agent_id THEN '✅ 一致'
        WHEN cs.agent_id IS NULL AND hr.agent_id IS NOT NULL THEN '❌ 会话未更新'
        WHEN cs.agent_id IS NOT NULL AND hr.agent_id IS NULL THEN '❌ 请求未分配'
        ELSE '❌ 不一致'
    END AS consistency_check
FROM chat_session cs
INNER JOIN handoff_request hr ON cs.handoff_request_id = hr.id
WHERE cs.chat_mode = 'AGENT'
ORDER BY cs.create_time DESC
LIMIT 10;

-- ============================================
-- 4. 检查转人工事件流
-- ============================================
-- 查看完整的事件流，验证流程是否正确
SELECT 
    he.id,
    he.handoff_request_id,
    he.event_type,  -- CREATED -> ASSIGNED -> ACCEPTED -> COMPLETED
    he.operator_id,
    he.operator_type,  -- USER, SYSTEM, AGENT
    he.event_data,
    he.create_time
FROM handoff_event he
WHERE he.handoff_request_id IN (
    SELECT id FROM handoff_request ORDER BY create_time DESC LIMIT 5
)
ORDER BY he.handoff_request_id, he.create_time;

-- ============================================
-- 5. 检查工单状态
-- ============================================
-- 验证工单是否正确创建和更新
SELECT 
    t.id AS ticket_id,
    t.tenant_id,
    t.user_id,
    t.session_id,
    t.title,
    t.status,  -- PENDING -> PROCESSING -> DONE
    t.priority,
    t.assignee,  -- 应该等于 agent_id
    t.create_time,
    t.update_time
FROM ticket t
WHERE t.session_id IN (
    SELECT session_id FROM handoff_request ORDER BY create_time DESC LIMIT 5
)
ORDER BY t.create_time DESC;

-- ============================================
-- 6. 检查消息记录
-- ============================================
-- 验证转人工后的消息是否正确记录
SELECT 
    cm.id AS message_id,
    cm.session_id,
    cm.sender_type,  -- USER, AGENT, SYSTEM
    cm.content,
    cm.create_time
FROM chat_message cm
WHERE cm.session_id IN (
    SELECT session_id FROM handoff_request WHERE status IN ('ACCEPTED', 'IN_PROGRESS', 'COMPLETED')
    ORDER BY create_time DESC LIMIT 5
)
ORDER BY cm.session_id, cm.create_time;

-- ============================================
-- 7. 统计分析
-- ============================================
-- 统计各状态的转人工请求数量
SELECT 
    status,
    COUNT(*) AS count,
    AVG(TIMESTAMPDIFF(SECOND, create_time, assigned_at)) AS avg_assign_time_seconds,
    AVG(TIMESTAMPDIFF(SECOND, assigned_at, accepted_at)) AS avg_accept_time_seconds,
    AVG(TIMESTAMPDIFF(SECOND, accepted_at, completed_at)) AS avg_complete_time_seconds
FROM handoff_request
WHERE create_time >= DATE_SUB(NOW(), INTERVAL 1 DAY)
GROUP BY status;

-- ============================================
-- 8. 查找问题会话
-- ============================================
-- 查找 agent_id 为 null 但 chat_mode 为 AGENT 的异常会话
SELECT 
    cs.id AS session_id,
    cs.tenant_id,
    cs.user_id,
    cs.agent_id,
    cs.chat_mode,
    cs.handoff_request_id,
    hr.status AS handoff_status,
    hr.agent_id AS handoff_agent_id,
    cs.create_time
FROM chat_session cs
LEFT JOIN handoff_request hr ON cs.handoff_request_id = hr.id
WHERE cs.chat_mode = 'AGENT' 
  AND cs.agent_id IS NULL
ORDER BY cs.create_time DESC;

-- ============================================
-- 9. 修复异常会话（如果需要）
-- ============================================
-- 如果发现异常会话，可以使用以下 SQL 修复
-- 注意：请先备份数据，然后根据实际情况修改条件

-- 示例：将 handoff_request 的 agent_id 同步到 chat_session
-- UPDATE chat_session cs
-- INNER JOIN handoff_request hr ON cs.handoff_request_id = hr.id
-- SET cs.agent_id = hr.agent_id
-- WHERE cs.chat_mode = 'AGENT' 
--   AND cs.agent_id IS NULL 
--   AND hr.agent_id IS NOT NULL
--   AND hr.status IN ('ASSIGNED', 'ACCEPTED', 'IN_PROGRESS');

-- ============================================
-- 10. 清理测试数据（可选）
-- ============================================
-- 如果需要清理测试数据，可以使用以下 SQL
-- 注意：请谨慎使用，确保不会删除生产数据

-- 删除测试会话的消息
-- DELETE FROM chat_message WHERE session_id IN (SELECT id FROM chat_session WHERE user_id = <test_user_id>);

-- 删除测试转人工事件
-- DELETE FROM handoff_event WHERE handoff_request_id IN (SELECT id FROM handoff_request WHERE user_id = <test_user_id>);

-- 删除测试转人工请求
-- DELETE FROM handoff_request WHERE user_id = <test_user_id>;

-- 删除测试工单
-- DELETE FROM ticket WHERE user_id = <test_user_id>;

-- 删除测试会话
-- DELETE FROM chat_session WHERE user_id = <test_user_id>;
