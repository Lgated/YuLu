# YuLu 本地开发依赖说明

本文档用于说明：**把仓库拉到本地后，需要先安装/准备哪些环境与依赖**，以及最小可运行步骤。

## 1. 基础开发工具

建议先安装以下工具：

- **Git**（用于拉取代码）
- **JDK 17**（后端基于 Spring Boot 2.7，`pom.xml` 中声明 `java.version=17`）
- **Maven 3.8+**（或直接使用仓库自带 `./mvnw`）
- **Node.js 18+**（前端为 Vite + React + TypeScript）
- **npm 9+**（随 Node 安装）

---

## 2. 后端运行前需要准备的中间件

根据 `src/main/resources/application.yml.example`，后端默认会连接以下服务：

1. **MySQL 8.x**（默认库名 `yulu`）
2. **Redis 6.x/7.x**
3. **RabbitMQ 3.x**
4. **Qdrant**（向量检索）

此外，AI 功能需要配置通义千问 API Key（环境变量 `AI_QIANWEN_API_KEY`）。

> 如果你只做部分模块开发，可按实际需要关闭/跳过对应功能；但完整联调建议把以上服务都启动。

---

## 3. 拉库后先执行什么

### 3.1 后端依赖下载

在仓库根目录执行：

```bash
./mvnw -q -DskipTests dependency:resolve
```

说明：

- 会把 Maven 依赖下载到本地仓库（`~/.m2/repository`）
- 首次执行时间较长属正常

### 3.2 前端依赖下载

进入前端目录执行：

```bash
cd frontend
npm install
```

说明：

- 会根据 `package.json` / `package-lock.json` 下载前端依赖
- 会生成 `frontend/node_modules`（该目录不需要提交到 Git）

---

## 4. 配置文件建议

建议使用示例配置文件，不要把真实密钥直接写入仓库：

```bash
cp src/main/resources/application.yml.example src/main/resources/application.yml
```

然后按本机环境修改：

- MySQL 账号密码
- Redis / RabbitMQ 连接信息
- `AI_QIANWEN_API_KEY`
- Qdrant 地址端口

---

## 5. 启动方式（最小流程）

### 5.1 启动后端

在仓库根目录：

```bash
./mvnw spring-boot:run
```

### 5.2 启动前端

在 `frontend` 目录：

```bash
npm run dev
```

---

## 6. 常见问题

- **依赖下载慢**：可配置 Maven / npm 国内镜像
- **后端启动报数据库连接错误**：先确认 MySQL 已启动且已创建 `yulu` 数据库
- **消息队列报错**：确认 RabbitMQ 用户名、密码、端口与配置一致
- **AI 调用失败**：优先检查 API Key 和 base-url 配置

---

如果你希望，我可以再补一版「一键本地初始化脚本（含 MySQL/Redis/RabbitMQ/Qdrant 的 Docker Compose）」到仓库里，拉库后执行一次就能跑起来。
