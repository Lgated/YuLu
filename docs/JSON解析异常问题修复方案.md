# JSON 解析异常问题修复方案

## 一、问题分析

### 1.1 问题现象

三个测试用例在执行时抛出异常：

1. **testDetectEmotion_JsonParseFailureFallback**：
   - 模型返回：`"用户情绪：愤怒"`（非JSON文本）
   - 代码执行：`objectMapper.readTree(json)` 抛出 `JsonParseException`
   - 虽然被 catch 并回退，但**不应该先抛异常再catch**

2. **testChat_InvalidJsonFallback**：
   - 模型返回：`"您好，我是客服助手，有什么可以帮助您的吗？"`（非JSON文本）
   - 代码执行：`objectMapper.readTree(rawText)` 抛出 `JsonParseException`
   - 虽然被 catch 并回退，但**不应该先抛异常再catch**

3. **testDetectEmotion_RuleFallback_Thanks**：
   - 模型调用直接抛出 `RuntimeException`（这是正常的，因为测试就是模拟API失败）

### 1.2 问题根源

**核心问题**：代码在解析 JSON 之前，**没有先判断文本是否是有效的 JSON 格式**。

**当前代码流程**：
```
模型返回文本 → 直接尝试解析 JSON → 抛出异常 → catch 异常 → 回退
```

**期望的流程**：
```
模型返回文本 → 判断是否是JSON → 如果是JSON则解析，如果不是则直接回退
```

### 1.3 问题定位

#### 问题点 1：detectEmotion 方法

**文件**：`src/main/java/com/ityfz/yulu/common/ai/impl/LangChain4jQwenClient.java`  
**位置**：第 127-132 行

**当前代码**：
```java
String json = model.chat(msgs).aiMessage().text();
ObjectMapper mapper = new ObjectMapper();
JsonNode node = mapper.readTree(json);  // ❌ 直接解析，如果是非JSON会抛异常
String emotion = node.path("emotion").asText("NEUTRAL").toUpperCase();
return emotion;
```
[text](obsidian://open?vault%3Ddocs%26file%3D%E5%89%8D%E7%AB%AFAPI%E8%B7%AF%E5%BE%84%E4%BF%AE%E6%94%B9%E8%AF%B4%E6%98%8E)
**问题**：没有先判断 `json` 是否是有效的 JSON 格式。

#### 问题点 2：parseChatResult 方法

**文件**：`src/main/java/com/ityfz/yulu/common/ai/impl/LangChain4jQwenClient.java`  
**位置**：第 163 行

**当前代码**：
```java
JsonNode root = objectMapper.readTree(rawText);  // ❌ 直接解析，如果是非JSON会抛异常
```

**问题**：没有先判断 `rawText` 是否是有效的 JSON 格式。

---

## 二、解决方案

### 2.1 方案一：先判断是否为 JSON（推荐）

**思路**：在解析 JSON 之前，先判断文本是否是有效的 JSON 格式。如果不是，直接回退，不抛异常。

#### 2.1.1 创建 JSON 校验工具方法

在 `LangChain4jQwenClient` 类中添加一个私有方法：

```java
/**
 * 判断字符串是否是有效的 JSON 格式
 * @param text 待判断的文本
 * @return true 如果是有效JSON，false 否则
 */
private boolean isValidJson(String text) {
    if (text == null || text.trim().isEmpty()) {
        return false;
    }
    try {
        objectMapper.readTree(text);
        return true;
    } catch (Exception e) {
        return false;
    }
}
```

#### 2.1.2 修改 detectEmotion 方法

**修改前**：
```java
try {
    List<ChatMessage> msgs = new ArrayList<>();
    msgs.add(SystemMessage.from(
            "你是一个情绪分析助手，请根据用户这句话判断情绪。" +
                    "只返回 JSON：{ \"emotion\": \"HAPPY|ANGRY|NEUTRAL\" }，不要输出其他任何文字。"
    ));
    msgs.add(UserMessage.from(text));

    String json = model.chat(msgs).aiMessage().text();
    ObjectMapper mapper = new ObjectMapper();
    JsonNode node = mapper.readTree(json);  // ❌ 直接解析
    String emotion = node.path("emotion").asText("NEUTRAL").toUpperCase();
    return emotion;

} catch (Exception e) {
    log.warn("[LLM] 情绪识别失败，回退到规则实现。text={}", text, e);
    return fallbackRuleEmotion(text);
}
```

**修改后**：
```java
try {
    List<ChatMessage> msgs = new ArrayList<>();
    msgs.add(SystemMessage.from(
            "你是一个情绪分析助手，请根据用户这句话判断情绪。" +
                    "只返回 JSON：{ \"emotion\": \"HAPPY|ANGRY|NEUTRAL\" }，不要输出其他任何文字。"
    ));
    msgs.add(UserMessage.from(text));

    String json = model.chat(msgs).aiMessage().text();
    
    // ✅ 先判断是否是有效JSON
    if (!isValidJson(json)) {
        log.debug("[LLM] 模型返回非JSON格式，回退到规则实现。text={}, response={}", text, json);
        return fallbackRuleEmotion(text);
    }
    
    // ✅ 确认是JSON后再解析
    ObjectMapper mapper = new ObjectMapper();
    JsonNode node = mapper.readTree(json);
    String emotion = node.path("emotion").asText("NEUTRAL").toUpperCase();
    return emotion;

} catch (Exception e) {
    // 这里只处理真正的异常（如API调用失败、网络错误等）
    log.warn("[LLM] 情绪识别失败，回退到规则实现。text={}", text, e);
    return fallbackRuleEmotion(text);
}
```

#### 2.1.3 修改 parseChatResult 方法

**修改前**：
```java
private ChatResult parseChatResult(String rawText) {
    ChatResult result = new ChatResult();
    try {
        JsonNode root = objectMapper.readTree(rawText);  // ❌ 直接解析
        // ... 解析逻辑
    } catch (Exception e) {
        log.warn("[LangChain4jQwenClient] 解析 JSON 失败，使用原始文本作为 answer，text={}", rawText, e);
        result.setAnswer(rawText);
        result.setEmotion("NORMAL");
        result.setIntent("GENERAL");
    }
    return result;
}
```

**修改后**：
```java
private ChatResult parseChatResult(String rawText) {
    ChatResult result = new ChatResult();
    
    // ✅ 先判断是否是有效JSON
    if (!isValidJson(rawText)) {
        log.debug("[LangChain4jQwenClient] 模型返回非JSON格式，使用原始文本作为 answer，text={}", rawText);
        result.setAnswer(rawText);
        result.setEmotion("NORMAL");
        result.setIntent("GENERAL");
        return result;
    }
    
    // ✅ 确认是JSON后再解析
    try {
        JsonNode root = objectMapper.readTree(rawText);
        String answer = root.path("answer").asText(null);
        if (answer == null || answer.isEmpty()) {
            answer = rawText;
        }
        result.setAnswer(answer);
        result.setEmotion(root.path("emotion").asText("NORMAL").toUpperCase());
        result.setIntent(root.path("intent").asText("GENERAL").toUpperCase());
    } catch (Exception e) {
        // 这里只处理解析过程中的其他异常（理论上不应该到这里，因为已经判断过是JSON了）
        log.warn("[LangChain4jQwenClient] JSON 解析过程出错，使用原始文本作为 answer，text={}", rawText, e);
        result.setAnswer(rawText);
        result.setEmotion("NORMAL");
        result.setIntent("GENERAL");
    }
    return result;
}
```

---

### 2.2 方案二：使用更宽松的 JSON 解析（备选）

**思路**：使用 Jackson 的 `JsonParser.Feature` 来容忍一些非标准格式，但这种方法不够可靠。

**不推荐**：因为模型可能返回完全不是 JSON 的文本（如纯中文描述）。

---

## 三、完整代码修改示例

### 3.1 添加 isValidJson 方法

在 `LangChain4jQwenClient` 类中添加（可以放在 `parseChatResult` 方法之前）：

```java
/**
 * 判断字符串是否是有效的 JSON 格式
 * @param text 待判断的文本
 * @return true 如果是有效JSON，false 否则
 */
private boolean isValidJson(String text) {
    if (text == null || text.trim().isEmpty()) {
        return false;
    }
    String trimmed = text.trim();
    // 快速检查：JSON 应该以 { 或 [ 开头
    if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
        return false;
    }
    try {
        objectMapper.readTree(trimmed);
        return true;
    } catch (Exception e) {
        return false;
    }
}
```

### 3.2 修改 detectEmotion 方法（完整版）

```java
@Override
public String detectEmotion(String text) {
    if (text == null || text.isEmpty()) {
        return "NORMAL";
    }

    try {
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(SystemMessage.from(
                "你是一个情绪分析助手，请根据用户这句话判断情绪。" +
                        "只返回 JSON：{ \"emotion\": \"HAPPY|ANGRY|NEUTRAL\" }，不要输出其他任何文字。"
        ));
        msgs.add(UserMessage.from(text));

        String json = model.chat(msgs).aiMessage().text();
        
        // 先判断是否是有效JSON
        if (!isValidJson(json)) {
            log.debug("[LLM] 模型返回非JSON格式，回退到规则实现。text={}, response={}", text, json);
            return fallbackRuleEmotion(text);
        }
        
        // 确认是JSON后再解析
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(json);
        String emotion = node.path("emotion").asText("NEUTRAL").toUpperCase();
        return emotion;

    } catch (Exception e) {
        // 处理真正的异常（API调用失败、网络错误等）
        log.warn("[LLM] 情绪识别失败，回退到规则实现。text={}", text, e);
        return fallbackRuleEmotion(text);
    }
}
```

### 3.3 修改 parseChatResult 方法（完整版）

```java
private ChatResult parseChatResult(String rawText) {
    ChatResult result = new ChatResult();
    
    // 先判断是否是有效JSON
    if (!isValidJson(rawText)) {
        log.debug("[LangChain4jQwenClient] 模型返回非JSON格式，使用原始文本作为 answer，text={}", rawText);
        result.setAnswer(rawText);
        result.setEmotion("NORMAL");
        result.setIntent("GENERAL");
        return result;
    }
    
    // 确认是JSON后再解析
    try {
        JsonNode root = objectMapper.readTree(rawText);
        String answer = root.path("answer").asText(null);
        if (answer == null || answer.isEmpty()) {
            answer = rawText;
        }
        result.setAnswer(answer);
        result.setEmotion(root.path("emotion").asText("NORMAL").toUpperCase());
        result.setIntent(root.path("intent").asText("GENERAL").toUpperCase());
    } catch (Exception e) {
        // 理论上不应该到这里，因为已经判断过是JSON了
        // 但为了安全，还是保留异常处理
        log.warn("[LangChain4jQwenClient] JSON 解析过程出错，使用原始文本作为 answer，text={}", rawText, e);
        result.setAnswer(rawText);
        result.setEmotion("NORMAL");
        result.setIntent("GENERAL");
    }
    return result;
}
```

---

## 四、实施步骤

### 步骤 1：添加 isValidJson 方法

1. 打开文件：`src/main/java/com/ityfz/yulu/common/ai/impl/LangChain4jQwenClient.java`
2. 在 `parseChatResult` 方法之前添加 `isValidJson` 方法
3. 复制上面的完整代码

### 步骤 2：修改 detectEmotion 方法

1. 定位到 `detectEmotion` 方法（约第 111 行）
2. 在 `String json = model.chat(msgs).aiMessage().text();` 之后
3. 添加 JSON 格式判断逻辑
4. 只有在确认是 JSON 后才进行解析

### 步骤 3：修改 parseChatResult 方法

1. 定位到 `parseChatResult` 方法（约第 160 行）
2. 在方法开始处添加 JSON 格式判断
3. 如果不是 JSON，直接设置结果并返回
4. 只有在确认是 JSON 后才进行解析

### 步骤 4：验证修改

1. 运行测试：
   ```bash
   mvn test -Dtest=LangChain4jQwenClientTest
   ```
2. 检查结果：
   - ✅ 所有测试应该通过
   - ✅ 不应该有 `JsonParseException` 堆栈输出
   - ✅ 非JSON文本应该直接回退，不抛异常

---

## 五、方案优势

### 5.1 性能优势

- **避免不必要的异常**：先判断再解析，避免创建异常对象
- **提前返回**：非JSON文本直接回退，不进入解析流程

### 5.2 代码清晰度

- **逻辑明确**：先判断格式，再决定处理方式
- **异常处理更精确**：catch 块只处理真正的异常（API失败、网络错误等）

### 5.3 可维护性

- **易于理解**：代码流程清晰，符合"先判断后处理"的原则
- **易于调试**：日志更精确，能区分"非JSON格式"和"真正的异常"

---

## 六、注意事项

### 6.1 isValidJson 方法的实现

- **快速检查**：先检查是否以 `{` 或 `[` 开头，快速过滤明显不是JSON的文本
- **完整验证**：再用 `readTree` 完整验证，确保是有效JSON

### 6.2 日志级别

- **非JSON格式**：使用 `log.debug()`，因为这是"预期的回退场景"
- **真正的异常**：使用 `log.warn()`，因为这是"真正的错误"

### 6.3 测试覆盖

修改后需要验证：
- ✅ 正常JSON格式能正确解析
- ✅ 非JSON格式能直接回退，不抛异常
- ✅ API调用失败能正确处理
- ✅ 空文本、null 等边界情况

---

## 七、总结

### 7.1 问题根源

代码在解析 JSON 之前**没有先判断文本是否是有效的 JSON 格式**，导致：
- 非JSON文本会抛出 `JsonParseException`
- 虽然被 catch 并回退，但**不应该先抛异常再catch**

### 7.2 解决方案

**核心思路**：先判断，再解析
1. 添加 `isValidJson` 方法判断文本是否是有效JSON
2. 在 `detectEmotion` 和 `parseChatResult` 中，先判断格式
3. 如果不是JSON，直接回退，不抛异常
4. 如果是JSON，再进行解析

### 7.3 预期效果

修改后：
- ✅ 非JSON文本直接回退，不抛异常
- ✅ 真正的异常（API失败等）仍能正确处理
- ✅ 代码逻辑更清晰，性能更好
- ✅ 测试用例全部通过，无异常堆栈输出

---

**文档完成时间**：2025-01-14  
**适用版本**：LangChain4jQwenClient v1.0


































