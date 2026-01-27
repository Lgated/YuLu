# LangChain4jQwenClient 单元测试说明

## 测试文件位置

`src/test/java/com/ityfz/yulu/common/ai/impl/LangChain4jQwenClientTest.java`

## 测试覆盖范围

### 1. `chat` 方法测试

- ✅ **正常 JSON 结构化输出**：验证模型返回标准 JSON 时，能正确解析 `answer` 字段
- ✅ **JSON 解析失败回退**：验证模型返回非 JSON 文本时，能回退使用原始文本作为 answer
- ✅ **空 answer 处理**：验证 JSON 中 `answer` 为空时，使用原始 JSON 文本
- ✅ **带历史上下文**：验证多轮对话时，能正确传递历史消息给模型
- ✅ **emotion 和 intent 解析**：验证 `ChatResult` 中的 `emotion` 和 `intent` 字段被正确解析（通过反射验证 ThreadLocal）
- ✅ **上下文消息顺序**：验证历史消息的顺序和数量正确传递

### 2. `detectEmotion` 方法测试

- ✅ **正常 JSON 返回**：验证模型返回标准 JSON 时，能正确提取 `emotion` 字段
- ✅ **JSON 解析失败回退**：验证模型返回非 JSON 时，能回退到关键字规则
- ✅ **空文本处理**：验证空字符串和 null 时返回 `NORMAL`，且不调用模型
- ✅ **规则回退机制**：
  - 包含"退货"关键词 → 返回 `ANGRY`
  - 包含"谢谢"关键词 → 返回 `HAPPY`
  - 无关键词 → 返回 `NEUTRAL`
- ✅ **异常处理**：验证模型调用抛出异常时，能正确回退到规则

## 运行测试

### 方式一：使用 Maven

```bash
# 运行所有测试
mvn test

# 只运行 LangChain4jQwenClientTest
mvn test -Dtest=LangChain4jQwenClientTest

# 运行特定测试方法
mvn test -Dtest=LangChain4jQwenClientTest#testChat_ValidJsonOutput
```

### 方式二：使用 IDE

1. **IntelliJ IDEA**：
   - 右键点击测试类或测试方法
   - 选择 "Run 'LangChain4jQwenClientTest'" 或 "Run 'testChat_ValidJsonOutput'"

2. **Eclipse**：
   - 右键点击测试类
   - 选择 "Run As" → "JUnit Test"

## 测试技术说明

### Mock 策略

由于 `LangChain4jQwenClient` 使用 `OpenAiChatModel`（具体类，可能为 final），测试采用以下策略：

1. **使用反射注入 Mock**：
   - 在 `setUp()` 中创建真实的 `LangChain4jQwenClient` 实例
   - 使用反射将内部的 `model` 字段替换为 Mock 对象
   - 这样避免了真实 API 调用，测试更快、更稳定

2. **Mock 模型响应**：
   - 通过 `mockChatModelResponse()` 辅助方法模拟模型返回
   - 可以精确控制返回的 JSON 格式，测试各种边界情况

### 测试数据示例

```java
// 正常 JSON 响应
"{\"answer\":\"您好，有什么可以帮助您的吗？\",\"emotion\":\"HAPPY\",\"intent\":\"GENERAL\"}"

// 非 JSON 响应（触发回退）
"您好，我是客服助手，有什么可以帮助您的吗？"

// 情绪识别 JSON
"{\"emotion\":\"ANGRY\"}"
```

## 预期测试结果

所有测试应该通过（绿色 ✅），包括：

- 结构化输出解析正确
- 异常情况能正确回退
- 情绪识别规则回退机制正常
- 边界情况（null、空字符串）处理正确

## 注意事项

1. **不需要真实 API Key**：测试使用 Mock，不调用真实 API
2. **线程安全**：`lastResult` 使用 `ThreadLocal`，每个测试方法独立运行，不会相互影响
3. **反射使用**：测试中使用了反射来访问私有字段，这是为了测试内部状态，生产代码中不建议这样做

## 扩展测试建议

如果后续需要更全面的测试，可以考虑：

1. **集成测试**：创建 `@SpringBootTest` 测试，使用真实的配置和模型（需要 API Key）
2. **性能测试**：测试大量并发请求下的表现
3. **边界测试**：测试超长文本、特殊字符、Unicode 等边界情况













