# RAG 客服对话测试步骤

本文档提供完整的测试步骤，验证客服对话 + RAG 功能是否正常工作。

---

## 一、准备工作

### 1.1 确保服务运行
- 后端服务已启动（端口 8080）
- Qdrant 服务已启动并连接成功
- 数据库连接正常
- Redis 连接正常

### 1.2 获取 JWT Token
使用管理员账号登录，获取 JWT Token：

```bash
POST http://localhost:8080/api/admin/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "your_password"
}
```

响应示例：
```json
{
  "success": true,
  "code": "200",
  "message": "OK",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
}
```

**保存 token，后续所有请求都需要在 Header 中携带：**
```
Authorization: Bearer {token}
```

---

## 二、上传测试文档

### 2.1 上传文档1：员工手册

```bash
POST http://localhost:8080/api/admin/document/upload
Authorization: Bearer {token}
Content-Type: multipart/form-data

file: [选择文件: docs/test-knowledge/测试文档1-星云科技员工手册.txt]
title: 星云科技员工手册
source: 内部文档
```

**记录返回的 documentId**（例如：`docId1 = 1`）

### 2.2 上传文档2：产品说明

```bash
POST http://localhost:8080/api/admin/document/upload
Authorization: Bearer {token}
Content-Type: multipart/form-data

file: [选择文件: docs/test-knowledge/测试文档2-星云AI助手产品说明.txt]
title: 星云AI助手产品说明
source: 产品文档
```

**记录返回的 documentId**（例如：`docId2 = 2`）

### 2.3 上传文档3：客户服务政策

```bash
POST http://localhost:8080/api/admin/document/upload
Authorization: Bearer {token}
Content-Type: multipart/form-data

file: [选择文件: docs/test-knowledge/测试文档3-星云科技客户服务政策.txt]
title: 星云科技客户服务政策
source: 服务文档
```

**记录返回的 documentId**（例如：`docId3 = 3`）

### 2.4 上传文档4：开发规范

```bash
POST http://localhost:8080/api/admin/document/upload
Authorization: Bearer {token}
Content-Type: multipart/form-data

file: [选择文件: docs/test-knowledge/测试文档4-星云科技内部开发规范.txt]
title: 星云科技内部开发规范
source: 技术文档
```

**记录返回的 documentId**（例如：`docId4 = 4`）

---

## 三、索引文档

### 3.1 索引文档1

```bash
POST http://localhost:8080/api/admin/knowledge/document/{docId1}/index
Authorization: Bearer {token}
```

等待响应：`{"success": true, "code": "200", "message": "操作成功", "data": null}`

### 3.2 索引文档2

```bash
POST http://localhost:8080/api/admin/knowledge/document/{docId2}/index
Authorization: Bearer {token}
```

### 3.3 索引文档3

```bash
POST http://localhost:8080/api/admin/knowledge/document/{docId3}/index
Authorization: Bearer {token}
```

### 3.4 索引文档4

```bash
POST http://localhost:8080/api/admin/knowledge/document/{docId4}/index
Authorization: Bearer {token}
```

**注意**：索引过程可能需要几秒到几十秒，取决于文档大小。可通过日志查看索引进度。

---

## 四、测试客服对话 + RAG

### 4.1 测试用例1：有检索结果的 RAG 对话

**测试问题**：`"星云科技的工作时间是什么？"`

**预期**：
- 应检索到员工手册中的工作时间信息
- AI 回答应包含"周一至周五，上午9:00-12:00，下午14:00-18:00"
- `refs` 数组应包含至少1条引用，`title` 为"星云科技员工手册"

**请求**：
```bash
POST http://localhost:8080/api/customer/chat/ask
Authorization: Bearer {token}
Content-Type: application/json

{
  "question": "星云科技的工作时间是什么？"
}
```

**验证点**：
1. `data.aiMessage.content` 中包含工作时间信息
2. `data.refs` 不为空，且至少有一条引用的 `title` 为"星云科技员工手册"
3. 回答准确，不编造信息

---

### 4.2 测试用例2：产品相关问题

**测试问题**：`"星云AI助手支持哪些接入方式？"`

**预期**：
- 应检索到产品说明文档中的多渠道接入信息
- AI 回答应包含"网页聊天窗口、微信小程序、APP内嵌、API接口"
- `refs` 中包含"星云AI助手产品说明"的引用

**请求**：
```bash
POST http://localhost:8080/api/customer/chat/ask
Authorization: Bearer {token}
Content-Type: application/json

{
  "question": "星云AI助手支持哪些接入方式？"
}
```

---

### 4.3 测试用例3：服务政策问题

**测试问题**：`"如果对服务不满意，可以退款吗？"`

**预期**：
- 应检索到客户服务政策中的退款政策
- AI 回答应包含"7天内可申请全额退款"等信息
- `refs` 中包含"星云科技客户服务政策"的引用

**请求**：
```bash
POST http://localhost:8080/api/customer/chat/ask
Authorization: Bearer {token}
Content-Type: application/json

{
  "question": "如果对服务不满意，可以退款吗？"
}
```

---

### 4.4 测试用例4：无检索结果的问题

**测试问题**：`"今天天气怎么样？"`

**预期**：
- 知识库中无相关内容，`refs` 为空数组
- AI 应按常规客服方式回复（不强制引用知识库）
- 回答应友好，但不会编造知识库内容

**请求**：
```bash
POST http://localhost:8080/api/customer/chat/ask
Authorization: Bearer {token}
Content-Type: application/json

{
  "question": "今天天气怎么样？"
}
```

**验证点**：
1. `data.refs` 为空数组或不存在
2. AI 回答不包含知识库中的具体信息
3. 回答语气友好，符合客服风格

---

### 4.5 测试用例5：多轮对话（上下文 + RAG）

**第一轮**：
```bash
POST http://localhost:8080/api/customer/chat/ask
Authorization: Bearer {token}
Content-Type: application/json

{
  "sessionId": null,
  "question": "星云科技的年假是多少天？"
}
```

**记录返回的 `sessionId`**（从响应中获取，或查看 `data.aiMessage.sessionId`）

**第二轮**（使用同一个 sessionId）：
```bash
POST http://localhost:8080/api/customer/chat/ask
Authorization: Bearer {token}
Content-Type: application/json

{
  "sessionId": {从第一轮获取的sessionId},
  "question": "那满三年后是多少天？"
}
```

**预期**：
- 第二轮应理解"满三年后"指的是年假
- 应检索到"满三年后年假增至20天"
- AI 回答应结合第一轮的上下文（知道在问年假）和第二轮的检索结果

---

### 4.6 测试用例6：技术规范问题

**测试问题**：`"代码提交信息格式是什么？"`

**预期**：
- 应检索到开发规范中的提交规范
- AI 回答应包含"类型(范围): 描述"格式
- `refs` 中包含"星云科技内部开发规范"的引用

**请求**：
```bash
POST http://localhost:8080/api/customer/chat/ask
Authorization: Bearer {token}
Content-Type: application/json

{
  "question": "代码提交信息格式是什么？"
}
```

---

## 五、验证要点

### 5.1 RAG 功能验证
- ✅ **检索准确性**：问题能检索到相关知识库内容
- ✅ **回答准确性**：AI 回答基于检索到的内容，不编造
- ✅ **引用完整性**：`refs` 数组包含正确的文档信息（title、source、score等）
- ✅ **无检索处理**：当知识库无相关内容时，`refs` 为空，AI 按常规客服回复

### 5.2 对话上下文验证
- ✅ **多轮对话**：使用相同 `sessionId` 的对话能记住上下文
- ✅ **上下文注入**：Redis 中存储的对话历史能正确传递给 LLM
- ✅ **RAG + 上下文**：RAG 检索结果与对话历史能同时生效

### 5.3 系统功能验证
- ✅ **情绪识别**：负向情绪能触发工单创建（可选测试）
- ✅ **会话管理**：会话列表、历史消息查询正常
- ✅ **租户隔离**：不同租户的数据互不干扰

---

## 六、常见问题排查

### 6.1 索引失败
**现象**：调用索引接口后返回错误或超时

**排查**：
1. 检查 Qdrant 服务是否运行：`docker ps | grep qdrant` 或查看 Qdrant 日志
2. 检查集合是否创建：查看日志中是否有"集合创建成功"或"集合已存在"
3. 检查 embedding 服务：查看 DashScope API 调用是否成功

### 6.2 检索无结果
**现象**：问题明明在文档中，但 `refs` 为空

**排查**：
1. 确认文档已索引：调用 `GET /api/admin/knowledge/document/{id}` 查看 `status` 是否为 1
2. 检查 `minScore`：默认 0.55，可能过高，可尝试降低到 0.3
3. 检查问题表述：尝试用文档中的原句或关键词提问

### 6.3 AI 回答不准确
**现象**：AI 回答与知识库内容不符

**排查**：
1. 查看日志中 `buildRagAugment` 的检索结果
2. 检查 `refs` 是否包含相关文档
3. 查看 LangChain4jQwenClient 的日志，确认 System 提示和用户消息是否正确

### 6.4 多轮对话失效
**现象**：第二轮对话不记得第一轮的内容

**排查**：
1. 确认 `sessionId` 在两轮请求中一致
2. 检查 Redis 中是否有 `chat:context:{sessionId}` 的 key
3. 查看日志中"历史上下文数量"是否为 0

---

## 七、测试数据总结

| 文档 | 文档ID | 测试问题示例 | 预期检索内容 |
|------|--------|-------------|-------------|
| 员工手册 | docId1 | "工作时间是什么？" | 周一至周五 9:00-18:00 |
| 员工手册 | docId1 | "年假多少天？" | 15天，满三年20天 |
| 产品说明 | docId2 | "支持哪些接入方式？" | 网页、微信、APP、API |
| 产品说明 | docId2 | "价格方案有哪些？" | 基础版2999、专业版9999等 |
| 服务政策 | docId3 | "可以退款吗？" | 7天内全额退款 |
| 服务政策 | docId3 | "技术支持响应时间？" | P1级30分钟，P2级2小时 |
| 开发规范 | docId4 | "代码提交格式？" | 类型(范围): 描述 |
| 开发规范 | docId4 | "日志级别有哪些？" | DEBUG、INFO、WARN、ERROR |

---

## 八、Postman 测试集合（可选）

可以创建 Postman Collection，包含：
1. 登录接口（获取 token）
2. 上传文档接口（4个文档）
3. 索引文档接口（4个文档）
4. 客服对话接口（6个测试用例）

使用 Postman 的 Environment Variables 存储 `token` 和 `sessionId`，方便测试。

---

完成以上测试后，RAG 客服对话功能应能正常工作。如有问题，请查看日志并参考"常见问题排查"部分。






















