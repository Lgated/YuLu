## 智链客服中台：下一步演进路线图（建议）

> 当前状态：  
> - 单体应用（Spring Boot 2.7 + MyBatis Plus）  
> - 已打通「多租户注册登录 + JWT + 租户上下文 + 会话/消息表 + Redis 上下文 + Mock 大模型 + Chat 接口」闭环  
> - 还未拆分微服务、未接入真实大模型、工单/知识库/ES/MQ 等尚未落地  

下面按**优先级**和**复杂度**给出推荐路线，你可以按阶段推进，每一小步都能形成可演示成果。

---

## 阶段一：把“AI 对话能力”做真实、好用（优先级最高）

- **1. 接入真实大模型（替换 MockLLMClient）**
  - 在 `common.ai` 下新增例如 `OpenAiClient` / `QianWenClient` 等实现类，继续实现现有的 `LLMClient` 接口（保持调用方不变）。
  - 从配置文件读取大模型的 `apiKey`、`baseUrl` 等，不要写死在代码里。
  - 在 `ChatServiceImpl` 里仍然只依赖 `LLMClient`，通过 `@Qualifier` 或配置切换不同模型实现。
  - 输出结果：  
    - `/api/chat/ask` 真正调用云端大模型，可以看到“智能且多轮”的回复。

- **2. 优化对话上下文策略**
  - 现在 Redis 里只保留最近 10 条上下文，可增加：  
    - 根据 token 长度动态裁剪上下文（防止超上下文长度）。  
    - 针对不同租户配置不同的 `CONTEXT_LIMIT`。  
  - 可以把 Redis 中的 `chat:context:{sessionId}` 做一个简易的管理接口（仅调试用）：  
    - GET `/api/chat/context/{sessionId}`：查看当前会话 Redis 上下文。

- **3. 基础监控 & 日志**
  - 统一在调用大模型时打业务日志：租户、用户、问题长度、耗时、是否命中缓存等。  
  - 目的：后续做“命中率分析 / 延迟统计”时有原始数据支撑。

---

## 阶段二：增强多租户与安全能力（保证可对外用）

- **4. 补齐 RBAC / 权限模型**
  - 扩展 `User.role` 的使用场景：`ADMIN / AGENT / USER`。  
  - 在 Controller 层按角色控制权限（哪类角色可以看哪类会话、工单、报表）。  
  - 先在单体里用简单的注解/判断（不用急着上 Spring Security 全套）。

- **5. 多租户隔离自测与压测**
  - 造两个以上租户（如：电商租户、教育租户），分别登录验证：  
    - 用户只能看到本租户的会话和消息。  
    - Redis context key 中虽然是 `chat:context:{sessionId}`，但由于 `sessionId` 本身带 `tenant_id` 约束 + 数据库层租户过滤，逻辑上仍然隔离。
  - 使用 JMeter / Postman Collection 做简单并发压测，观察：  
    - 多租户下 `TenantContextHolder` 是否会串租户（ThreadLocal 是否正确清理）。  

- **6. 完善错误处理和统一返回**
  - 已有 `BizException` + `GlobalExceptionHandler`，可以再梳理一遍：  
    - 为登录、权限、租户缺失、会话不存在等定义清晰的错误码和 message。  
    - 让前端容易根据 code 做统一处理（例如 `TENANT_REQUIRED` 跳登录页）。

---

## 阶段三：引入“情绪 + 工单”闭环（体现业务价值）

- **7. 基于情绪触发工单系统（先做单体版本）**
  - 新增 `ticket` 模块（同一个工程内）：  
    - 表结构：`ticket`（工单主表）、`ticket_comment`（工单跟进记录）。  
    - 字段包括：`tenant_id`、`user_id`、`session_id`、`status`、`priority`、`assignee` 等。
  - 在 `ChatServiceImpl.chatWithAi` 中：  
    - 对 `llmClient.detectEmotion(question)` 结果做判断，如果为 `NEGATIVE`，则自动创建一张工单（状态：待处理），并关联当前 `chat_session`。  
  - 提供简单的工单接口：  
    - `GET /api/ticket/list`：按租户 + 状态分页查询工单。  
    - `POST /api/ticket/assign`：管理员把工单分配给某个客服。  
  - 这一阶段先不急着上 MQ，直接在方法里同步创建工单即可。

- **8. 引入 MQ 解耦“对话服务”和“工单服务”（可选进阶）**
  - 把“情绪触发工单”这一步改成发 MQ 消息：  
    - 发送：`NEGATIVE_EMOTION_DETECTED` 事件，包含 `tenantId`、`userId`、`sessionId`、原始问题文本等。  
    - 消费者：工单模块监听消息，异步创建工单。  
  - 这样可以避免对话接口被工单创建逻辑拖慢，有利于后续拆服务。

---

## 阶段四：知识库 & ES 检索（增强问答质量）

- **9. 知识库模块雏形**
  - 新增 `knowledge` 模块（仍在单体内）：  
    - 表：`kb_doc`（知识条目），字段：`tenant_id`、`title`、`content`、`status`、`tags` 等。  
  - 提供上传/维护接口（暂时可以只支持纯文本或 FAQ）。

- **10. 接入 ES 做全文检索**
  - 将 `kb_doc` 同步到 ES 中，建立索引（可先用简单字段匹配，不急着上向量检索）。  
  - 在 `ChatServiceImpl.chatWithAi` 中：  
    - 先用 ES 根据用户问题检索候选文档，将检索结果作为“知识上下文”拼到 Prompt 里，再交给大模型。  
  - 形成“知识库辅助的大模型问答”，提升命中率和可控性。

---

## 阶段五：服务拆分与基础设施升级

- **11. 按业务边界拆分微服务（有必要时再做）**
  - 优先拆的候选服务：  
    - User/Tenant/Auth 服务  
    - Chat/Conversation 服务  
    - Ticket 工单服务  
    - Knowledge/ES 服务  
  - 使用 Spring Cloud（Nacos + Gateway + OpenFeign）做服务发现与网关。  
  - 这一步工作量大、复杂度高，**建议在前面几个阶段稳定后再做**，否则会把精力耗在基础设施上而不是业务亮点上。

- **12. 调度 & 报表**
  - 接入 XXL-JOB：  
    - 定时任务：会话归档、工单超时扫描、满意度报表统计等。  
  - 统计指标：  
    - AI 命中率（多少问题无需人工介入）。  
    - 工单转人工比例、处理时长。  
    - 不同租户/不同行业的使用对比。

---

## 简要优先级总结（给你一个清晰顺序）

1. **先接入真实大模型**（替换 Mock，实现真正可用的 AI 对话）。  
2. **完善多租户安全 & RBAC**（保证对外可用，不串数据）。  
3. **用情绪识别串起工单闭环**（体现“智能客服 + 人工兜底”的业务价值）。  
4. **做知识库 + ES 检索**（提升问答质量，做出“懂业务”的客服）。  
5. **最后再考虑拆微服务 / 上 MQ / XXL-JOB**（把前面的能力稳定后再工程化演进）。  

这样推进，每一阶段都可以写到简历上，并且都有明确的“业务故事 + 技术亮点”，也方便你逐步扩展项目，而不是一上来就把所有技术栈堆在一起难以维护。












