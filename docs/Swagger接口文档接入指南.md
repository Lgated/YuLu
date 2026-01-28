## Swagger / OpenAPI 接入指南（YuLu）

### 目标
- **自动生成**全量接口文档（OpenAPI 3.0）
- **按模块分组**（B 端 / C 端 / 知识库 / 通知）
- **支持 JWT 鉴权**（Swagger UI 里可直接 `Authorize`）
- **文档内容详细**：接口用途、参数说明、权限说明、返回结构

---

### 1) 选型：springdoc-openapi
你的项目是 **Spring Boot 2.7.18**，推荐用 **springdoc-openapi 1.x**：
- 依赖：`org.springdoc:springdoc-openapi-ui:1.7.0`
- 默认提供：
  - OpenAPI JSON：`/v3/api-docs`
  - Swagger UI：`/swagger-ui.html`

---

### 2) Maven 依赖（已接入）
修改 `pom.xml` 增加：

```xml
<dependency>
  <groupId>org.springdoc</groupId>
  <artifactId>springdoc-openapi-ui</artifactId>
  <version>1.7.0</version>
</dependency>
```

---

### 3) application.yml 配置（已接入）
在 `src/main/resources/application.yml` 增加：

```yaml
springdoc:
  api-docs:
    enabled: true
    path: /v3/api-docs
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
    display-request-duration: true
    operations-sorter: alpha
    tags-sorter: alpha
```

---

### 4) 分组与 JWT 鉴权配置（已接入）
新增 `src/main/java/com/ityfz/yulu/common/config/OpenApiConfig.java`，做两件事：

#### 4.1 OpenAPI 基本信息 + Bearer JWT
- 在文档右上角会出现 `Authorize`
- 你可以填：`Bearer <token>`（也支持只填 token，取决于 swagger-ui 行为；建议带 Bearer）

#### 4.2 按路径分组（GroupedOpenApi）
按你当前项目目录和 API 前缀做分组：
- **B端**
  - `/api/admin/auth/**`
  - `/api/admin/dashboard/**`
  - `/api/admin/ticket/**`
  - `/api/admin/session/**`
  - `/api/admin/user/**`
  - `/api/admin/user-management/**`
- **C端**
  - `/api/customer/auth/**`
  - `/api/customer/chat/**`
  - `/api/customer/faq/**`
- **知识库**
  - `/api/admin/document/**`
  - `/api/admin/knowledge/**`
- **通知**
  - `/api/notify/**`

---

### 5) Controller 注释规范（已落地示例）
为了让文档“详细、结构分明”，建议每个 Controller / 方法都加注释：

#### 5.1 类级别：@Tag
```java
@Tag(name = "B端-工单（Admin/Ticket）", description = "租户端工单管理：列表、派单、状态流转、备注、统计")
```

#### 5.2 方法级别：@Operation
```java
@Operation(summary = "分页查询工单列表", description = "ADMIN：可看全租户工单；AGENT：仅返回分配给自己的工单（由后端按角色自动过滤）")
```

#### 5.3 参数：@Parameter
```java
public ApiResponse<IPage<Ticket>> list(
  @Parameter(description = "工单状态（可选）") @RequestParam(required = false) String status,
  @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") int page
)
```

#### 5.4 返回码：建议补充 @ApiResponses（可选增强）
如果你希望文档更“企业级”，下一步可以为关键接口补齐：
- 200：成功
- 401：未登录
- 403：无权限（@RequireRole）
- 400：参数错误（@Valid）

---

### 6) 已补充 Swagger 注释的接口（本次）
已对以下 Controller 增加 `@Tag/@Operation/@Parameter` 等注释：
- `AdminAuthController`
- `AdminTicketController`
- `DocumentController`
- `CustomerAuthController`
- `CustomerChatController`
- `KnowledgeIndexController`
- `KnowledgeSearchController`
- `KnowledgeRagController`
- `NotifyController`

你项目其余 Controller（如 `AdminDashboardController`、`AdminSessionController`、`AdminUserManagementController`、`CustomerFAQController` 等）建议按相同规范继续补齐。

---

### 7) 如何访问与使用
1. 启动后端服务
2. 打开 Swagger UI：
   - `http://localhost:8080/swagger-ui.html`
3. 登录拿到 token 后，点 `Authorize`，填：
   - `Bearer <你的token>`
4. 选择分组（右上角下拉）查看不同模块的接口文档

---

### 8) 常见问题
#### 8.1 Swagger UI 能打开，但接口调用 403？
这是正常的：你的业务接口上有 `@RequireRole`，未携带 token 或角色不匹配会被拒绝。
解决：在 Swagger UI 里 `Authorize` 注入 token。

#### 8.2 你用了 JwtAuthFilter，会不会影响 swagger？
你当前 `JwtAuthFilter` **不会拦截**请求（只负责解析 token 并设置上下文），因此不会阻止 swagger 的静态资源或 `/v3/api-docs`。







