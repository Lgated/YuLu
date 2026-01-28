# 通义千问 API 配置指南

## 📋 问题说明

如果遇到 `404 Not Found` 错误，通常是 API 地址配置不正确导致的。

## 🔧 解决方案

### 方案一：使用兼容模式（推荐）

兼容模式使用 OpenAI 兼容的 API 格式，更容易集成。

**配置 `application.yml`**：
```yaml
ai:
  qianwen:
    api-key: your_api_key_here
    base-url: https://dashscope.aliyuncs.com/compatible/v1
    model: qwen-turbo  # 或 qwen-plus
```

**支持的模型**：
- `qwen-turbo`：快速响应
- `qwen-plus`：平衡性能和速度
- `qwen-max`：最强性能

### 方案二：使用标准模式

标准模式使用通义千问原生 API 格式。

**配置 `application.yml`**：
```yaml
ai:
  qianwen:
    api-key: your_api_key_here
    base-url: https://dashscope.aliyuncs.com/api/v1
    model: qwen1.5-110b-chat
```

## 🚀 快速修复步骤

1. **打开配置文件**：`src/main/resources/application.yml`

2. **修改配置**（推荐使用兼容模式）：
   ```yaml
   ai:
     qianwen:
       api-key: sk-b7fbdd371bc247548f1fc31057fe5713
       base-url: https://dashscope.aliyuncs.com/compatible/v1
       model: qwen-turbo
   ```

3. **重启项目**

4. **测试接口**：
   ```bash
   POST http://localhost:8080/api/chat/ask
   Headers:
     Authorization: Bearer {your_token}
   Body:
   {
     "sessionId": null,
     "question": "你好"
   }
   ```

## 🔍 常见错误

### 错误 1：404 Not Found

**原因**：API 地址配置错误

**解决方案**：
- 检查 `base-url` 是否正确
- 兼容模式：`https://dashscope.aliyuncs.com/compatible/v1`
- 标准模式：`https://dashscope.aliyuncs.com/api/v1`

### 错误 2：401 Unauthorized

**原因**：API Key 无效或过期

**解决方案**：
- 检查 API Key 是否正确
- 确认 API Key 是否有效
- 在阿里云控制台重新生成 API Key

### 错误 3：400 Bad Request

**原因**：请求体格式错误或模型名称不正确

**解决方案**：
- 检查模型名称是否正确
- 兼容模式推荐使用：`qwen-turbo`、`qwen-plus`、`qwen-max`
- 标准模式使用：`qwen1.5-110b-chat` 等

## 📝 API Key 获取方式

1. **访问阿里云百炼控制台**：https://dashscope.console.aliyun.com/

2. **创建 API Key**：
   - 登录后进入「API-KEY 管理」
   - 点击「创建新的 API Key」
   - 复制生成的 API Key

3. **配置到项目**：
   - 将 API Key 填入 `application.yml` 的 `api-key` 字段

## 🎯 代码说明

项目已自动支持两种模式：

- **兼容模式**：自动检测 URL 中包含 `/compatible`，使用 OpenAI 兼容格式
- **标准模式**：使用通义千问原生格式

代码会自动根据 `base-url` 判断使用哪种模式，无需手动切换。

## 📚 相关文档

- [通义千问官方文档](https://help.aliyun.com/zh/model-studio/)
- [兼容模式 API 文档](https://help.aliyun.com/zh/model-studio/developer-reference/api-details-9)


















