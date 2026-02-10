-- ============================================
-- 转人工功能数据库验证查询
-- ============================================
-- 用于检查 chat_session 和 handoff_request 的数据一致性
-- ============================================

-- 1. 查看最近的转人工请求
SELECT 
    hr.id AS handoff_id,
    hr.session_id,
    hr.agent_id AS handoff_agent_id,
    hr.status AS handoff_status,
    hr.created_at,
    hr.assigned_at,
    cs.agent_id AS session_agent_id,
    cs.status AS session_status,
    cs.chat_mode,
    CASE 
        WHEN hr.agent_id IS NULL THEN '❌ 未分配客服'
        WHEN cs.agent_id IS NULL THEN '❌ 会话未更新 agent_id'
        WHEN hr.agent_id != cs.agent_id THEN '❌ agent_id 不一致'
        ELSE '✅ 正常'
    END AS check_result
FROM handoff_request hr
LEFT JOIN chat_session cs ON hr.session_id = cs.id
WHERE hr.tenant_id = 1
ORDER BY hr.created_at DESC
LIMIT 10;

-- 2. 查找问题会话（agent_id 不一致）
SELECT 
    cs.id AS session_id,
    cs.agent_id AS session_agent_id,
    cs.handoff_request_id,
    hr.agent_id AS handoff_agent_id,
    hr.status AS handoff_status,
    '需要修复' AS issue
FROM chat_session cs
INNER JOIN handoff_request hr ON cs.handoff_request_id = hr.id
WHERE cs.tenant_id = 1
  AND hr.status = 'ASSIGNED'
  AND (cs.agent_id IS NULL OR cs.agent_id != hr.agent_id);

-- 3. 统计转人工状态
SELECT 
    hr.status,
    COUNT(*) AS count,
    COUNT(CASE WHEN cs.agent_id IS NULL THEN 1 END) AS missing_session_agent_id,
    COUNT(CASE WHEN cs.agent_id IS NOT NULL THEN 1 END) AS has_session_agent_id
FROM handoff_request hr
LEFT JOIN chat_session cs ON hr.session_id = cs.id
WHERE hr.tenant_id = 1
  AND hr.created_at > DATE_SUB(NOW(), INTERVAL 1 DAY)
GROUP BY hr.status;

-- 4. 查看特定会话的详细信息（替换 SESSION_ID）
-- SELECT 
--     cs.id AS session_id,
--     cs.user_id,
--     cs.agent_id AS session_agent_id,
--     cs.handoff_request_id,
--     cs.status AS session_status,
--     cs.chat_mode,
--     cs.create_time,
--     hr.id AS handoff_id,
--     hr.agent_id AS handoff_agent_id,
--     hr.status AS handoff_status,
--     hr.created_at AS handoff_created_at,
--     hr.assigned_at AS handoff_assigned_at
-- FROM chat_session cs
-- LEFT JOIN handoff_request hr ON cs.handoff_request_id = hr.id
-- WHERE cs.id = SESSION_ID;

-- 5. 修复问题会话（仅在确认问题后执行）
-- 将 handoff_request 的 agent_id 同步到 chat_session
-- UPDATE chat_session cs
-- INNER JOIN handoff_request hr ON cs.handoff_request_id = hr.id
-- SET cs.agent_id = hr.agent_id
-- WHERE cs.tenant_id = 1
--   AND hr.status = 'ASSIGNED'
--   AND cs.agent_id IS NULL;

-- 6. 查看客服当前负载（从数据库角度）
SELECT 
    u.id AS agent_id,
    u.username AS agent_name,
    COUNT(cs.id) AS current_sessions,
    ac.max_concurrent_sessions
FROM user u
LEFT JOIN agent_config ac ON u.id = ac.user_id AND u.tenant_id = ac.tenant_id
LEFT JOIN chat_session cs ON u.id = cs.agent_id 
    AND cs.status = 1 
    AND cs.chat_mode = 'AGENT'
WHERE u.tenant_id = 1
  AND u.role = 'AGENT'
GROUP BY u.id, u.username, ac.max_concurrent_sessions;

-- 7. 查看最近的聊天消息（验证消息是否正常发送）
-- SELECT 
--     cm.id,
--     cm.session_id,
--     cm.sender_type,
--     cm.content,
--     cm.create_time,
--     cs.agent_id
-- FROM chat_message cm
-- INNER JOIN chat_session cs ON cm.session_id = cs.id
-- WHERE cm.session_id = SESSION_ID
-- ORDER BY cm.create_time DESC
-- LIMIT 20;
