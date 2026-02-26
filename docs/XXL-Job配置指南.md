# XXL-Job 配置指南

## 📋 概述

XXL-Job 是一个分布式任务调度平台，本项目使用它来执行定时任务（如：工单超时自动关闭）。

## 🎯 两种方案

### 方案一：禁用 XXL-Job（推荐，如果暂时不需要定时任务）

如果暂时不需要定时任务功能，可以禁用 XXL-Job，避免连接错误。

**已自动配置**：项目已修改为可选启用，默认禁用。

**验证禁用状态**：
- 检查 `application.yml` 中 `xxl.job.enabled: false`
- 启动项目时不会出现 XXL-Job 连接错误

---

### 方案二：完整配置 XXL-Job（如果需要定时任务）

如果需要使用定时任务功能（如工单超时自动关闭），需要完整配置 XXL-Job。

## 🚀 完整配置步骤

### 步骤 1：初始化 XXL-Job 数据库

1. **执行 SQL 脚本**：
   ```bash
   mysql -u root -p < docs/sql/xxl-job-init.sql
   ```
   或在数据库工具中执行 `docs/sql/xxl-job-init.sql`

2. **验证数据库创建**：
   ```sql
   USE xxl_job;
   SHOW TABLES;
   ```
   应该看到以下表：
   - xxl_job_registry
   - xxl_job_group
   - xxl_job_info
   - xxl_job_log
   - xxl_job_log_report
   - xxl_job_logglue
   - xxl_job_user

### 步骤 2：下载并启动 XXL-Job Admin

1. **下载 XXL-Job**：
   - 访问：https://github.com/xuxueli/xxl-job/releases
   - 下载最新版本的 `xxl-job-admin-2.x.x.jar`

2. **配置数据库连接**：
   创建 `application.properties` 文件（与 jar 包同目录）：
   ```properties
   # 数据库配置
   spring.datasource.url=jdbc:mysql://localhost:3306/xxl_job?useUnicode=true&characterEncoding=UTF-8&autoReconnect=true&serverTimezone=Asia/Shanghai
   spring.datasource.username=root
   spring.datasource.password=yfz
   spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
   
   # 登录配置
   xxl.job.login.username=admin
   xxl.job.login.password=123456
   
   # 服务端口
   server.port=18080
   ```

3. **启动 XXL-Job Admin**：
   ```bash
   java -jar xxl-job-admin-2.4.0.jar
   ```

4. **访问管理界面**：
   打开浏览器访问：http://localhost:18080/xxl-job-admin
   - 用户名：`admin`
   - 密码：`123456`

### 步骤 3：配置项目启用 XXL-Job

1. **修改 `application.yml`**：
   ```yaml
   xxl:
     job:
       enabled: true  # 启用 XXL-Job
       admin:
         addresses: http://localhost:18080/xxl-job-admin
       executor:
         appname: yulu-ticket-service
         address:
         ip: 127.0.0.1
         port: 9999
         logpath: ./logs/xxl-job/jobhandler
         logretentiondays: 30
       accessToken:  # 如果 XXL-Job Admin 配置了 accessToken，这里需要填写
   ```

2. **启动项目**：
   ```bash
   mvn spring-boot:run
   ```

3. **验证执行器注册**：
   - 登录 XXL-Job Admin 管理界面
   - 进入「执行器管理」
   - 应该看到 `yulu-ticket-service` 执行器已注册

### 步骤 4：配置定时任务

1. **登录 XXL-Job Admin**：http://localhost:18080/xxl-job-admin

2. **进入「任务管理」**，点击「新增」

3. **配置工单超时自动关闭任务**：
   - **执行器**：选择 `yulu-ticket-service`
   - **任务描述**：工单超时自动关闭
   - **路由策略**：第一个
   - **Cron**：`0 0 2 * * ?`（每天凌晨2点执行）
   - **运行模式**：BEAN
   - **JobHandler**：`ticketAutoClose`（对应代码中的 `@XxlJob("ticketAutoClose")`）
   - **阻塞处理策略**：单机串行
   - **任务超时时间**：600（秒）
   - **失败重试次数**：0

4. **启动任务**：点击「启动」按钮

## 🔧 常见问题

### 问题 1：Connection refused: getsockopt

**原因**：XXL-Job Admin 服务未启动

**解决方案**：
1. 确保 XXL-Job Admin 已启动（步骤 2）
2. 检查端口 18080 是否被占用
3. 检查防火墙设置

### 问题 2：执行器未注册

**原因**：执行器配置错误或网络不通

**解决方案**：
1. 检查 `application.yml` 中的 `xxl.job.admin.addresses` 是否正确
2. 检查执行器端口 9999 是否被占用
3. 检查防火墙是否阻止连接

### 问题 3：任务执行失败

**原因**：JobHandler 名称不匹配或代码错误

**解决方案**：
1. 检查 `TicketJobHandler` 中的 `@XxlJob("ticketAutoClose")` 注解
2. 确保 XXL-Job Admin 中的 JobHandler 名称与注解中的名称一致
3. 查看执行日志：`./logs/xxl-job/jobhandler/`

## 📝 项目中的定时任务

### 工单超时自动关闭任务

**位置**：`src/main/java/com/ityfz/yulu/ticket/job/TicketJobHandler.java`

**功能**：
- 查询超过 7 天未处理的工单（状态为 PENDING 或 PROCESSING）
- 批量更新状态为 CLOSED

**Cron 表达式**：`0 0 2 * * ?`（每天凌晨 2 点执行）

**JobHandler 名称**：`ticketAutoClose`

## 🎯 快速禁用/启用

### 禁用 XXL-Job

修改 `application.yml`：
```yaml
xxl:
  job:
    enabled: false  # 禁用
```

### 启用 XXL-Job

1. 确保 XXL-Job Admin 已启动
2. 修改 `application.yml`：
   ```yaml
   xxl:
     job:
       enabled: true  # 启用
   ```

## 📚 相关文档

- [XXL-Job 官方文档](https://www.xuxueli.com/xxl-job/)
- [项目概述文档](./project-overview.md)
- [数据库初始化脚本](./sql/xxl-job-init.sql)



































