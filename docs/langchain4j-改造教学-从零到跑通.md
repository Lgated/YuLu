# LangChain4j 改造教学（从零到跑通）- YuLu 项目

> 目标：**不改动你现有业务架构（`ChatServiceImpl -> LLMClient`）**的前提下，把“手写 HTTP 调用通义千问”替换为 **LangChain4j** 接入，并一步步教你理解：模型（Model）、消息（Message）、提示词（Prompt）、记忆（Memory）、工具（Tools/Function calling）、RAG（检索增强）怎么落在你的项目里。

---

## 0. 你现在的 AI 接入方式是什么？是否“老套”？

### 0.1 现状

你当前链路（简化）：

- `ChatController.ask()`  
  → `ChatServiceImpl.chatWithAi()`  
  → `LLMClient.chat(messages, question)`  
  → `QianWenClient`（手写 `RestTemplate` 调 DashScope）

### 0.2 “老套”在哪里（并非坏，只是低层）

这不是“错”，但属于**低层 HTTP 封装**，常见痛点：

- **接口/路径变化**：DashScope 原生接口 vs OpenAI 兼容接口路径不同，容易 404（你已经踩到）。
- **鉴权头差异**：Bearer vs `X-DashScope-API-Key`，稍不注意就 401/404。
- **请求/响应结构差异**：不同模式字段不同（`output.choices...` vs `choices...`）。
- **高级能力扩展成本高**：比如“对话记忆”“工具调用”“RAG”，手写会越来越复杂。

### 0.3 LangChain4j 能带来什么

- **统一抽象**：你不必关心底层 JSON 字段怎么变。
- **更标准的 Chat 编排**：消息结构、system prompt、temperature、超时、重试等更易维护。
- **可渐进升级**：先跑通，再引入 Memory/Tools/RAG。

---

## 1. 改造原则（强烈推荐）

### 1.1 不推翻现有架构，先做“平滑替换”

你已经有 `LLMClient` 接口，这是最好的“抽象隔离层”。  
建议做法：

- **保留** `LLMClient`
- **新增** `LangChain4jQwenClient implements LLMClient`
- 通过 Spring `@Qualifier` 或配置，决定注入哪个实现

这样你可以随时在 `QianWenClient` / `MockLLMClient` / `LangChain4jQwenClient` 之间切换，安全推进。

---

## 2. 第一步：准备 DashScope OpenAI 兼容接口（最容易跑通）

你目前 404 的根因大概率是：

- 你配置的 `base-url` 与代码拼接路径不匹配，或
- 你走了非兼容模式却用兼容路径，或
- 模型名/接口版本不匹配导致服务端路由不到

### 2.1 推荐固定使用兼容模式（对 LangChain4j 最友好）

建议你把“模型调用”统一走 DashScope OpenAI 兼容接口：

- Base URL（示例）：`https://dashscope.aliyuncs.com/compatible/v1`
- Path：`/chat/completions`
- Header：`Authorization: Bearer {DASHSCOPE_API_KEY}`

> 注意：兼容接口的模型名一般用 `qwen-turbo / qwen-plus / qwen-max` 这种（以官方为准）。

---

## 3. 第二步：引入 LangChain4j 依赖（你需要手动改 `pom.xml`）

在 `pom.xml` 的 `<properties>` 中加入版本：

```xml
<langchain4j.version>1.3.0</langchain4j.version>
```

在 `<dependencies>` 中加入（最小集）：

```xml
<dependency>
  <groupId>dev.langchain4j</groupId>
  <artifactId>langchain4j</artifactId>
  <version>${langchain4j.version}</version>
</dependency>
<dependency>
  <groupId>dev.langchain4j</groupId>
  <artifactId>langchain4j-open-ai</artifactId>
  <version>${langchain4j.version}</version>
</dependency>
```

然后执行：

```bash
mvn -q -DskipTests package
```

如果下载依赖慢，建议配置 Maven 镜像（阿里云/腾讯云）。

---

## 4. 第三步：新增一个 LangChain4j 版 LLMClient 实现（不改原有实现）

### 4.1 新增类：`LangChain4jQwenClient`

建议放在：`src/main/java/com/ityfz/yulu/common/ai/impl/LangChain4jQwenClient.java`

它要做的事：

- 读取你现有配置（可以复用 `QianWenProperties`）
- 创建 LangChain4j 的 `OpenAiChatModel`，但把 `baseUrl` 指向 DashScope 的兼容地址
- 把你现有 `List<Message>`（role/content）转成 LangChain4j 的消息结构
- 调用模型并返回字符串

示例代码（你照着抄即可，后续我会带你逐行解释）：

```java
package com.ityfz.yulu.common.ai.impl;

import com.ityfz.yulu.common.ai.LLMClient;
import com.ityfz.yulu.common.ai.Message;
import com.ityfz.yulu.common.config.QianWenProperties;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component("langChain4jQwenClient")
public class LangChain4jQwenClient implements LLMClient {

    private final OpenAiChatModel model;

    public LangChain4jQwenClient(QianWenProperties props) {
        // 关键：OpenAiChatModel 可以指定 baseUrl，接入 DashScope 的 OpenAI 兼容接口
        this.model = OpenAiChatModel.builder()
                .baseUrl(props.getBaseUrl()) // 例如 https://dashscope.aliyuncs.com/compatible/v1
                .apiKey(props.getApiKey())   // Bearer Token
                .modelName(props.getModel()) // 例如 qwen-turbo
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    @Override
    public String chat(List<Message> context, String question) {
        List<ChatMessage> messages = new ArrayList<>();

        // 可选：system 提示词（建议先固定一个最小版）
        messages.add(SystemMessage.from("你是一个专业的客服助手，回答要简洁、准确。"));

        // 把 Redis 上下文（role/content）映射为 LangChain4j 消息
        if (context != null) {
            for (Message m : context) {
                if (m == null) continue;
                String role = m.getRole();
                String content = m.getContent();
                if (content == null) content = "";
                if ("assistant".equalsIgnoreCase(role) || "ai".equalsIgnoreCase(role)) {
                    messages.add(AiMessage.from(content));
                } else {
                    messages.add(UserMessage.from(content));
                }
            }
        }

        // 再追加本轮用户问题
        messages.add(UserMessage.from(question));

        // 调用模型
        String answer = model.generate(messages);
        return answer;
    }

    @Override
    public String detectIntent(String text) {
        // 第一期先不做：保持与你现有接口一致
        return null;
    }

    @Override
    public String detectEmotion(String text) {
        // 第一期先用你现有规则/Mock；第二期我们再做“模型判别情绪”
        return "NEUTRAL";
    }
}
```

> 说明：这段代码的目标是“先跑通”。后面章节会教你把 `detectEmotion` 也改成模型调用，并且加入结构化输出。

---

## 5. 第四步：配置文件怎么配（你需要手动改 `application.yml`）

你现有配置段是：

```yaml
ai:
  qianwen:
    api-key: xxx
    base-url: xxx
    model: xxx
```

如果你要走 DashScope OpenAI 兼容模式，建议：

```yaml
ai:
  qianwen:
    api-key: ${AI_QIANWEN_API_KEY:填你自己的key}
    base-url: https://dashscope.aliyuncs.com/compatible/v1
    model: qwen1.5-110b-chat
```

> 强烈建议：**不要把 key 写死在仓库**，改用环境变量。

Windows（PowerShell）：

```powershell
setx AI_QIANWEN_API_KEY "你的key"
```

重开终端后生效。

---

## 6. 第五步：切换注入（让 ChatServiceImpl 用 LangChain4j）

你当前 `ChatServiceImpl` 构造器里写死了：

- `@Qualifier("qianWenClient")`

你需要手动把它改成：

- `@Qualifier("langChain4jQwenClient")`

或者更推荐：做成配置可切换，例如：

1）在 `application.yml` 加一项：

```yaml
ai:
  provider: langchain4j
```

2）写一个 `@Configuration` 提供 `@Primary LLMClient`，根据 `ai.provider` 返回不同实现。

> 这一段我建议你先用“直接改 Qualifier”跑通，等你熟悉后再做“配置化切换”。

---

## 7. LangChain4j 小白教学：你需要理解的 5 个核心概念

### 7.1 Model（模型）

LangChain4j 里，`OpenAiChatModel` 是一个“聊天模型实现”。  
它不一定真连 OpenAI —— 只要你的服务提供 **OpenAI 兼容接口**，就能复用它。

你现在接入 DashScope，就是这个思路。

### 7.2 Message（消息）

大模型聊天不是只有一个字符串，而是一组消息：

- System：设定角色/规则（例如“你是客服”）
- User：用户输入
- AI：模型输出

你现在 Redis 里保存的 `role/content`，本质就是“消息列表”，非常适合映射到 LangChain4j。

### 7.3 Prompt（提示词）

SystemMessage 就是最常用的 Prompt 入口。  
第一期：写死一个 system prompt  
第二期：按租户/按场景加载不同 prompt（比如电商/教育）

### 7.4 Memory（记忆）

你现在的“记忆”是：

- Redis List 保存最近 N 条上下文

LangChain4j 也有 memory 组件，但第一期你不一定要上。  
建议路径：

- 第一期：继续用你现有 Redis
- 第二期：把 Redis 封装成 LangChain4j 的 `ChatMemory`（更统一）

### 7.5 Tools（工具/函数调用）

你现在“负向情绪 -> 工单”是你写死判断的。  
未来可以升级为：

- 模型判断：是否需要创建工单
- 工具：`createTicket(...)` 作为一个工具给模型调用

这就是“Agent/Tools”的方向。

---

## 8. 推荐的改造路线（边学边写）

### 阶段 A（1 天内）：先跑通 LangChain4j

- 加依赖
- 新增 `LangChain4jQwenClient`
- 切换 `@Qualifier`
- `/api/chat/ask` 返回真实模型回复

### 阶段 B（2-3 天）：把情绪识别也做成模型能力

- 用一个专门 prompt，让模型输出 `NEUTRAL/NEGATIVE/ANGRY`（先文本）
- 再升级为 JSON 结构化输出（更稳）

### 阶段 C（3-7 天）：引入 RAG（知识库）

- 新增 `knowledge` 表
- 用 ES/向量库检索相关文档
- 把检索结果拼到 prompt（检索增强）

---

## 9. 你接下来我建议你怎么做（最小行动清单）

1. 手动改 `pom.xml` 加 LangChain4j 依赖（第 3 节）
2. 新增 `LangChain4jQwenClient` 类（第 4 节）
3. 把 `application.yml` 的 `ai.qianwen.base-url/model/api-key` 配好（第 5 节）
4. 在 `ChatServiceImpl` 把 `@Qualifier` 指向新实现（第 6 节）
5. 重启项目，用 Postman 调 `/api/chat/ask` 验证

---

## 10. 你要我继续怎么帮你（你选一个）

- A：我继续把“配置化切换 LLMClient（ai.provider）”的方案写进文档（更工程化）
- B：我教你把“情绪识别”改成 LangChain4j 的结构化输出（JSON）
- C：我给你做一套最小 RAG（MySQL 维护知识 + 简易检索 + prompt 拼接）



















