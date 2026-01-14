## 智链客服中台（单体版）多租户 + AI 对话实现步骤与架构思路

本文件记录在**单体 Spring Boot 项目里，先打通「多租户 + AI 对话」闭环**的详细实现思路和分步计划，后续可在此基础上拆分为微服务。

---

## 一、总体路线

- 当前目标：在现有单体项目中优先实现：
  - 多租户体系（租户隔离 + 用户登录 + JWT）
  - AI 对话（多轮对话上下文 + 简单意图/情绪识别）
- 先在单体内把业务链路跑通，再根据包结构拆到各个微服务模块。

建议包结构（单体版）：

- `com.ityfz.yulu`
  - `common`（通用）
    - `exception`（全局异常、业务异常）
    - `model`（统一返回、分页对象等）
    - `tenant`（租户上下文、拦截器）
    - `security`（JWT 工具、登录用户上下文）
    - `ai`（AI 客户端接口、策略实现）
  - `user`（用户 & 租户）
    - `controller`
    - `service`
    - `mapper`
    - `entity`
  - `chat`（对话模块）
    - `controller`
    - `service`
    - `mapper`
    - `entity`
  - `config`（MyBatis Plus、Redis、拦截器注册等配置）

`pom.xml` 需要的主要依赖：

- Spring Web
- Spring Data Redis
- MyBatis Plus + MySQL Driver
- Lombok
- Spring Validation
- JWT 相关库（如 jjwt 或 hutool-jwt）

---

## 二、阶段 1：多租户 & 用户体系打底

### 1.1 表结构设计（MySQL）

核心表（简化版，够用即可）：

- `tenant`（租户表）
  - `id`（主键）
  - `tenant_code`（租户编码，如 `ECOM_001`）
  - `name`
  - `status`（启用/禁用）
  - `create_time` / `update_time`

- `user`（用户表）
  - `id`
  - `username`
  - `password`（加密存储：如 BCrypt）
  - `tenant_id`
  - `role`（简单用字符串：`ADMIN` / `AGENT` / `USER`）
  - `status`
  - `create_time` / `update_time`

**说明：** 这里使用「单库 + 表字段 `tenant_id` 隔离」，后续拆微服务时再考虑多数据源 + 动态路由。

### 1.2 MyBatis Plus & 基础实体

- 在 `user.entity` 下创建：
  - `Tenant` 实体（对应 `tenant` 表）
  - `User` 实体（对应 `user` 表）

- 在 `user.mapper` 下创建：
  - `TenantMapper`
  - `UserMapper`

- 在 `user.service` 下创建：
  - `TenantService` / `TenantServiceImpl`
  - `UserService` / `UserServiceImpl`

### 1.3 登录与注册接口（单体内）

在 `user.controller` 下创建 `AuthController`：

- `POST /api/auth/registerTenant`
  - 请求体：租户基础信息 + 管理员账号信息
  - 逻辑：
    1. 创建租户记录 `tenant`
    2. 创建管理员 `user`（`role = ADMIN`，绑定 `tenant_id`）

- `POST /api/auth/login`
  - 请求体：`tenantCode` + `username` + `password`
  - 逻辑：
    1. 根据 `tenantCode` 查租户
    2. 按 `tenant_id + username` 查用户
    3. 校验密码
    4. 生成 JWT：内含 `userId`、`tenantId`、`role` 等

### 1.4 JWT 工具 & 登录用户模型

在 `common.security` 下：

- 定义 `LoginUser` 类：
  - 字段：`userId`、`tenantId`、`username`、`role` 等

- 定义 `JwtUtil` 工具类：
  - `generateToken(LoginUser user)`
  - `parseToken(String token)` → `LoginUser`

---

## 三、阶段 2：多租户上下文 & 拦截器（关键亮点）

### 2.1 租户上下文（ThreadLocal）

在 `common.tenant` 包下：

- `TenantContextHolder`
  - 内部使用 `ThreadLocal<Long>` 或对象 `TenantContext`（包含 tenantId、tenantCode 等）
  - 方法：
    - `setTenantId(Long tenantId)`
    - `getTenantId()`
    - `clear()`

可选：`UserContextHolder` 保存当前登录用户信息 `LoginUser`，同样基于 ThreadLocal。

### 2.2 登录拦截器（或过滤器）

在 `common.security` 下：

- 编写 `JwtAuthFilter`（继承 `OncePerRequestFilter`）或 `HandlerInterceptor`：
  - 从请求头 `Authorization: Bearer xxx` 解析 token
  - 通过 `JwtUtil.parseToken` 得到 `LoginUser`
  - 将 `tenantId` 写入 `TenantContextHolder`
  - 将 `LoginUser` 写入 `UserContextHolder`
  - 放行后续处理

在 `config` 下的 `WebMvcConfig` 注册过滤器/拦截器：

- `addInterceptors` / `addFilter`
- 排除登录、注册、健康检查等接口

### 2.3 MyBatis Plus 多租户插件（推荐）

在 `config` 中配置 MyBatis Plus：

- 注册 `MybatisPlusInterceptor`
- 添加 `TenantLineInnerInterceptor`
- 实现 `TenantLineHandler`：
  - `getTenantId()` 从 `TenantContextHolder.getTenantId()` 读取
  - 指定需要自动拼接 `tenant_id` 的表（如：`user`、`chat_session`、`chat_message` 等）

效果：Mapper 查询自动拼接 `WHERE tenant_id = ?`，简历可描述为：

> 基于 ThreadLocal + MyBatis Plus 多租户插件，实现数据层透明的租户隔离。

---

## 四、阶段 3：AI 对话数据模型 + Redis 上下文

### 3.1 会话 & 消息表设计

聊天相关表（最小可用集）：

- `chat_session`（会话表）
  - `id`
  - `tenant_id`
  - `user_id`
  - `session_title`（可选，如「关于订单 123 的咨询」）
  - `status`（进行中/已结束）
  - `create_time` / `update_time`

- `chat_message`（消息表）
  - `id`
  - `tenant_id`
  - `session_id`
  - `sender_type`（`USER` / `AI` / `AGENT`）
  - `content`
  - `emotion`（可选：`NORMAL` / `NEGATIVE` / `POSITIVE`）
  - `create_time`

### 3.2 Redis 设计（多轮上下文）

约定 Redis key 规则：

- 会话上下文：`chat:context:{sessionId}`
  - Value：保存最近 N 条对话（user 问 & AI 答），可以是：
    - JSON String（整个列表）
    - Redis List（每条消息一个元素）

在 `config` 下配置：

- `RedisTemplate<String, Object>` 或 `StringRedisTemplate`

### 3.3 Chat 模块基础类

在 `chat.entity`：

- `ChatSession`（对应 `chat_session`）
- `ChatMessage`（对应 `chat_message`）

在 `chat.mapper`：

- `ChatSessionMapper`
- `ChatMessageMapper`

在 `chat.service`：

- `ChatService` 接口（示例方法）：
  - `Long createSessionIfNotExists(Long userId, Long tenantId, String title)`
  - `ChatMessage userSendMessage(Long sessionId, String content)`
  - `List<ChatMessage> listMessages(Long sessionId)`

- `ChatServiceImpl`：
  - 负责写 MySQL & Redis：
    - 写入 `chat_session` / `chat_message`
    - 更新 Redis 中的会话上下文

---

## 五、阶段 4：AI 客户端 + 策略模式（先用 Mock 实现）

### 4.1 抽象 AI 客户端接口

在 `common.ai` 下：

- 定义 `LLMClient` 接口：
  - `String chat(List<Message> context, String question)`
  - `String detectIntent(String text)`
  - `String detectEmotion(String text)`

- 定义 `Message` 类：
  - 字段：`role`（`user` / `assistant`）
  - 字段：`content`

### 4.2 MockLLMClient 实现

在 `common.ai.impl` 下：

- `MockLLMClient` 实现 `LLMClient`：
  - `chat`：
    - 根据简单关键词返回固定模板回答（如包含「退款」就返回「为你处理退款相关问题……」）
  - `detectIntent`：
    - 简单规则：返回 `REFUND` / `INVOICE` / `COMPLAIN` / `GENERAL`
  - `detectEmotion`：
    - 简单规则：含有「差评」「投诉」「垃圾」等词判定为 `NEGATIVE`，否则 `NORMAL`

通过 Mock 实现可以先跑通业务链路，后续接入 OpenAI / 通义千问等时，只需新增实现类：

- `OpenAiClient`
- `QianwenClient`
- `WenxinClient`

并通过配置或策略模式进行选择。

### 4.3 ChatService 集成 LLMClient

在 `ChatServiceImpl` 中：

1. 根据 `sessionId` 从 Redis 拉取当前会话上下文（最近 N 条消息）。

2. 将上下文转换为 `List<Message>`。

3. 调用 `llmClient.chat(context, userQuestion)` 得到 AI 回复。

4. 调用 `llmClient.detectEmotion(userQuestion)` 得到情绪标签。

5. 将本轮：
   - 用户提问（`sender_type = USER`）
   - AI 回答（`sender_type = AI`）
   两条消息写入：
   - MySQL 表 `chat_message`
   - Redis 会话上下文 key：`chat:context:{sessionId}`（更新为最新 N 条）
   
   
   
   
   
   - ChatServiceImpl.chatWithAi() 是完整闭环：创建会话、写入用户消息→appendContext→读取 Redis 上下文→调用 LLMClient→写入 AI 回复→再次 appendContext。因此每次指定 sessionId 调用聊天接口时，都会把对话历史自动落到 chat:context:{sessionId} 这个 Key 下。

---

## 六、阶段 5：Chat 接口层（Controller）& 多租户打通

### 5.1 ChatController 设计

在 `chat.controller` 下创建 `ChatController`：

- `POST /api/chat/send`
  - 请求体：
    - `sessionId`（可选：为空则自动新建会话）
    - `content`（用户问题）
  - 处理流程：
    1. 从 `UserContextHolder` 中获取当前用户 & 租户信息（`userId`、`tenantId`）
    2. 若 `sessionId` 为空，则调用 `createSessionIfNotExists` 新建会话
    3. 调用 `chatService.userSendMessage(sessionId, content)`
    4. 返回：
       - `sessionId`
       - `answer`（AI 回复）
       - `intent` / `emotion`（如需要）

- `GET /api/chat/history/{sessionId}`
  - 返回该会话历史消息列表（可分页）

### 5.2 多租户在 Chat 流程中的体现

- 通过 `TenantContextHolder.getTenantId()`、`UserContextHolder.getUserId()`：
  - 所有对 `chat_session`、`chat_message` 的读写都自动带上 `tenant_id`
  - 若启用了 MyBatis Plus 多租户插件，Mapper 层无需手动拼接租户条件

- Redis key 可选加上租户前缀，避免冲突：
  - `chat:context:{tenantId}:{sessionId}`

---

## 七、阶段 6：负面情绪转人工（后续扩展预留点）

在当前阶段，可以先只做「情绪识别 + 标记」，不立即实现工单流转：

- 当 `detectEmotion` 返回 `NEGATIVE` 时：
  - 在 `ChatMessage` 中将 `emotion` 标记为 `NEGATIVE`
  - 预留 TODO：
    - 后续通过 MQ 向工单模块发送创建工单消息

这样为后续「工单服务」和「人工兜底」功能预埋扩展点。

---

## 八、阶段 7：整体验证 & 为微服务拆分做准备

完成上述步骤后，系统应具备：

- 多租户 + 用户登录：
  - 不同租户的用户只能看到自己的会话和对话记录
- 多轮 AI 对话：
  - Redis 保存上下文，多轮问答具有记忆能力
- 情绪识别：
  - 对对话记录打上情绪标签，后续可驱动工单/统计分析

后续拆分为微服务时，可按以下思路：

- 按包将当前单体拆为不同工程：
  - `user-service`（用户 & 租户 & 鉴权）
  - `ai-chat-service`（AI 对话）
  - `knowledge-service`（知识库）
  - `ticket-service`（工单）
  - `analytics-service`（统计分析）
  - `job-service`（定时任务）
- 将公共逻辑抽取为独立 `common` 工程：
  - `TenantContextHolder`
  - `JwtUtil` / `LoginUser`
  - `LLMClient` 及其实现
- 服务间本地调用改为：
  - 使用 OpenFeign 或 MQ 进行服务间通信

---

## 九、实现顺序建议（实践时的落地步骤）

1. 改造表结构：创建 `tenant`、`user`、`chat_session`、`chat_message` 表。
2. 搭建 `user` 模块：
   - 实体 / Mapper / Service / Controller
   - 完成租户注册 + 登录 + JWT 颁发
3. 实现 `TenantContextHolder`、`UserContextHolder`、`JwtAuthFilter`，完成多租户上下文传递。
4. 配置 MyBatis Plus 多租户插件，实现自动拼接 `tenant_id` 条件。
5. 实现 `chat` 模块：
   - 实体 / Mapper / Service（含 Redis 上下文）
   - `ChatController` 提供发送消息和历史查询接口
6. 抽象 `LLMClient` 接口，实现 `MockLLMClient`，打通「问题 -> 上下文 -> AI 回复 -> 写库+Redis」全链路。
7. 在消息中加入情绪识别字段，为后续工单模块预埋扩展点。

按以上步骤逐步实现，你可以一边写代码一边对照本文件，后期在简历中可以将这些实现描述为：

> 基于 Spring Boot + MyBatis Plus 实现多租户智能客服中台，在单体架构下完成用户/租户隔离、基于 ThreadLocal 的租户上下文传递、Redis 多轮对话上下文管理以及封装大模型接口的 AI 对话服务，为后续 Spring Cloud 微服务化改造打下基础。





![image-20251203225536770](C:\Users\86139\AppData\Roaming\Typora\typora-user-images\image-20251203225536770.png)
