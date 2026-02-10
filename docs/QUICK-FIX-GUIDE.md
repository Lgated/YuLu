# Quick Fix Guide - Handoff Functionality Issue

## 🚨 Problem Summary

**Error**: "当前会话未接入客服" (Current session has no agent assigned)

**Root Causes**:
1. `chat_session.agent_id` is NULL in database (even after agent assignment)
2. Redis key `agent:status:1:3` has wrong data type (STRING instead of HASH)

---

## ⚡ Quick Fix (3 Steps)

### Step 1: Restart Backend Service

```bash
# Stop current service (Ctrl+C)
# Then restart
mvn spring-boot:run
```

**Why**: Code fix is already in place, but requires restart to take effect.

---

### Step 2: Clean Redis Data

**Option A: Use Debug API** (Recommended)

```bash
# Replace YOUR_TOKEN with actual admin JWT token
TOKEN="eyJ0eXAiOiJKV1QiLCJhbGciOiJIUz..."

# Force reset agent status
curl -X POST "http://localhost:8080/api/admin/debug/force-reset-agent/3" \
  -H "Authorization: Bearer ${TOKEN}"

# Clear handoff queue
curl -X DELETE "http://localhost:8080/api/admin/debug/clear-queue" \
  -H "Authorization: Bearer ${TOKEN}"
```

**Option B: Direct Redis Commands**

```bash
redis-cli
DEL agent:status:1:3
DEL agent:sessions:1:3
DEL agent:online:1
exit
```

---

### Step 3: Agent Goes Online

**Frontend**: 
1. Refresh browser (F5)
2. Click "上线" (Go Online) button

**Or use API**:
```bash
curl -X PUT "http://localhost:8080/api/admin/user/online-status?status=ONLINE" \
  -H "Authorization: Bearer AGENT_TOKEN"
```

---

## ✅ Verification

### Check Redis Structure

```bash
curl -X GET "http://localhost:8080/api/admin/debug/check-agent-redis/3" \
  -H "Authorization: Bearer ${TOKEN}"
```

**Expected Output**:
```json
{
  "statusKeyType": "HASH",  // ✅ Must be HASH
  "statusData": {
    "status": "ONLINE",
    "current_sessions": 0,
    "max_sessions": 5
  }
}
```

### Test Handoff Flow

1. User requests handoff
2. Check backend logs for: `"已更新会话的客服ID"` (Updated session agent_id)
3. Verify database:
   ```sql
   SELECT id, agent_id FROM chat_session WHERE id = XX;
   -- agent_id should NOT be NULL
   ```
4. User sends message → Should work without error ✅

---

## 📋 Checklist

- [ ] Backend service restarted
- [ ] Redis `agent:status:1:3` deleted
- [ ] Agent went online
- [ ] Redis structure is HASH type
- [ ] Agent status shows "ONLINE"
- [ ] User can send messages successfully
- [ ] Agent receives messages

---

## 🔧 Debug APIs

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/admin/debug/check-agent-redis/{agentId}` | GET | Check Redis structure |
| `/api/admin/debug/force-reset-agent/{agentId}` | POST | Force reset agent |
| `/api/admin/debug/clear-queue` | DELETE | Clear handoff queue |
| `/api/admin/debug/agent-status/{agentId}` | GET | View agent status |

---

## 📖 Detailed Documentation

- **Complete Fix Steps**: `docs/完整修复步骤-立即执行.md`
- **Troubleshooting Checklist**: `docs/问题排查清单.md`
- **Database Queries**: `docs/数据库验证查询.sql`
- **Flow Diagrams**: `docs/转人工问题诊断流程图.md`
- **Final Solution**: `docs/转人工问题-最终解决方案.md`

---

## 🎯 Key Points

1. **Code Fix**: Already implemented in `HandoffService.asyncAssignAgent()`
   - Now updates both `handoff_request.agent_id` AND `chat_session.agent_id`

2. **Redis Issue**: Wrong data type
   - Was: `agent:status:1:3` (STRING) → timestamp only
   - Should be: `agent:status:1:3` (HASH) → full status object

3. **Fix Process**:
   - Restart backend → Clean Redis → Agent online → Test

---

## ❓ FAQ

**Q: What does `agent:status:1:3` mean?**
- Format: `agent:status:{tenantId}:{agentId}`
- `1` = tenantId (tenant ID)
- `3` = agentId (agent user ID)

**Q: How to release agent load?**
- Method 1: Agent ends session normally (auto decrements)
- Method 2: Use debug API to reset
- Method 3: Agent goes offline then online (resets to 0)

**Q: What about old sessions?**
- Option 1: Manually fix database (see `docs/数据库验证查询.sql`)
- Option 2: User creates new handoff request

---

## 📞 Still Not Working?

Provide these for support:

1. Backend logs (last 100 lines)
2. Redis check result: `GET /api/admin/debug/check-agent-redis/3`
3. Database query: `SELECT * FROM chat_session WHERE id = XX;`
4. Frontend console errors (if any)
