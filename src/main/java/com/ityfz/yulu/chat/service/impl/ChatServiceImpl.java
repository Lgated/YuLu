package com.ityfz.yulu.chat.service.impl;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ityfz.yulu.common.ai.LLMClient;
import com.ityfz.yulu.common.ai.Message;
import com.ityfz.yulu.common.enums.Roles;
import com.ityfz.yulu.common.enums.ErrorCodes;
import com.ityfz.yulu.common.exception.BizException;
import com.ityfz.yulu.common.security.SecurityUtil;
import com.ityfz.yulu.common.tenant.TenantContextHolder;
import com.ityfz.yulu.chat.entity.ChatMessage;
import com.ityfz.yulu.chat.entity.ChatSession;
import com.ityfz.yulu.chat.mapper.ChatMessageMapper;
import com.ityfz.yulu.chat.mapper.ChatSessionMapper;
import com.ityfz.yulu.chat.service.ChatService;
import com.ityfz.yulu.ticket.entity.Ticket;
import com.ityfz.yulu.ticket.event.NegativeEmotionEvent;
import com.ityfz.yulu.ticket.mq.TicketEventPublisher;
import com.ityfz.yulu.ticket.service.TicketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChatServiceImpl implements ChatService {
    private static final int CONTEXT_LIMIT = 10;
    private static final int DEFAULT_CONTEXT_CHAR_LIMIT = 4000; //默认4000个字符
    private static final int SMALL_TENANT_CHAR_LIMIT = 2000; //小租户2000个字符
    private static final int LARGE_TENANT_CHAR_LIMIT = 8000; //大租户8000个字符


    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final LLMClient llmClient;
    private final TicketService ticketService;
    private final TicketEventPublisher emotionEventPublisher;

    public ChatServiceImpl(ChatSessionMapper chatSessionMapper,
                           ChatMessageMapper chatMessageMapper,
                           StringRedisTemplate stringRedisTemplate,
                           @Qualifier("langChain4jQwenClient") LLMClient llmClient,
                           TicketService ticketService,
                           TicketEventPublisher emotionEventPublisher) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.llmClient = llmClient;
        this.ticketService = ticketService;
        this.emotionEventPublisher = emotionEventPublisher;
    }

    @Override
    @Transactional
    public Long createSessionIfNotExists(Long userId, Long tenantId, String title) {
        //SELECT *
        //FROM chat_session
        //WHERE tenant_id = ?
        //  AND user_id = ?
        //  AND status = 1
        //LIMIT 1;
        ChatSession session = chatSessionMapper.selectOne(
                Wrappers.<ChatSession>lambdaQuery()
                        .eq(ChatSession::getTenantId, tenantId)
                        .eq(ChatSession::getUserId, userId)
                        .eq(ChatSession::getStatus, 1)
                        .last("LIMIT 1")
        );
        if (session != null) {
            return session.getId();
        }

        session = new ChatSession();
        session.setTenantId(tenantId);
        session.setUserId(userId);
        session.setSessionTitle(title);
        session.setStatus(1);
        // 手动设置时间（因为还没有配置 MyBatis Plus 自动填充处理器）
        LocalDateTime now = LocalDateTime.now();
        session.setCreateTime(now);
        session.setUpdateTime(now);
        chatSessionMapper.insert(session);
        return session.getId();
    }

    @Override
    @Transactional
    public ChatMessage userSendMessage(Long sessionId, String content) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "缺少租户信息");
        }

        ChatMessage message = new ChatMessage();
        message.setTenantId(tenantId);
        message.setSessionId(sessionId);
        message.setSenderType("USER");
        message.setContent(content);
        message.setEmotion("NORMAL");
        message.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(message);

        appendContext(sessionId, "user", content);
        return message;
    }

    //根据sessionId获取消息列表
    @Override
    @Transactional
    public List<ChatMessage> listMessages(Long sessionId) {
        return chatMessageMapper.selectList(
                Wrappers.<ChatMessage>lambdaQuery()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getCreateTime)
        );
    }

    @Override
    @Transactional
    public void appendAiReply(Long sessionId, String aiContent, String emotion) {
        Long tenantId = TenantContextHolder.getTenantId();
        ChatMessage aiMsg = new ChatMessage();
        aiMsg.setTenantId(tenantId);
        aiMsg.setSessionId(sessionId);
        aiMsg.setSenderType("AI");
        aiMsg.setContent(aiContent);
        aiMsg.setEmotion(emotion);
        aiMsg.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(aiMsg);

        appendContext(sessionId, "assistant", aiContent);
    }

    //从 Redis 中取出当前会话最近的上下文，供调用 AI 模型时拼接 Prompt，或调试查看。
    @Override
    public List<Map<String, String>> listContextFromRedis(Long sessionId) {
        String key = buildContextKey(sessionId);
        //读取列表前10条
        List<String> jsonList = stringRedisTemplate.opsForList().range(key, 0, CONTEXT_LIMIT - 1);
//        列表为空，返回空集合
        if (CollectionUtils.isEmpty(jsonList)) {
            return Collections.emptyList();
        }
        //“JSON 字符串 → Map 对象，循环 10 次，攒成 List
        return jsonList.stream()
                .map(json -> JSONUtil.toBean(json, new TypeReference<Map<String, String>>() {
                }, true))
                .collect(Collectors.toList());
    }

    @Override
    public ChatMessage chatWithAi(Long sessionId, Long userId, Long tenantId, String question) {
        // 1. 填充租户上下文（保证 DB 操作正确）
        TenantContextHolder.setTenantId(tenantId);

        //// 2. 如果 sessionId 为空 / 不存在，可以自动创建
        if (sessionId == null) {
            sessionId = createSessionIfNotExists(userId, tenantId, "默认会话");
        }

        // 3. 先把用户提问写入 MySQL & Redis
        ChatMessage userMsg = new ChatMessage();
        userMsg.setTenantId(tenantId);
        userMsg.setSessionId(sessionId);
        userMsg.setSenderType("USER");
        userMsg.setContent(question);
        userMsg.setEmotion("NORMAL");   // 暂时 NORMAL，可后面用 detectEmotion 再识别
        userMsg.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(userMsg);



        // 4. 从 Redis 中取出当前会话最近的上下文，供调用 AI 模型时拼接 Prompt
        //并转成 List<Message>
        List<Map<String, String>> context = listContextFromRedis(sessionId);
        List<Message> messages = context.stream()
                .map(m -> new Message(m.get("role"), m.get("content")))
                .collect(Collectors.toList());

        String summary = stringRedisTemplate.opsForValue().get("chat:summary:" + sessionId);

        if (summary != null && !summary.isEmpty()) {
            messages.add(0, new Message("system",
                    "这是本次会话目前为止的摘要，请在回答问题时参考这些信息：" + summary));
        }

        // 5. 调用 AI
        String aiReply = llmClient.chat(messages, question);
        appendContext(sessionId, "user", question);


        // 6. 情绪识别（针对用户这句话，也可以针对整段对话）
        String emotion = llmClient.detectEmotion(question);

        // 7. 如果检测到负向情绪（NEGATIVE 或 ANGRY），自动创建工单
/*        if (isNegativeEmotion(emotion)) {
            try {

                // 根据情绪严重程度决定优先级：ANGRY -> HIGH, NEGATIVE -> MEDIUM
                String priority = "ANGRY".equalsIgnoreCase(emotion) ? "HIGH" : "MEDIUM";
                Ticket ticket = ticketService.createTicketOnNegative(tenantId, userId, sessionId, question, priority);
                log.info("[Chat] 检测到负向情绪，已自动创建工单: emotion={}, ticketId={}, sessionId={}, userId={}",
                        emotion, ticket.getId(), sessionId, userId);
            } catch (Exception e) {
                // 工单创建失败不应该影响对话流程，只记录错误日志
                log.error("[Chat] 创建工单失败: emotion={}, sessionId={}, userId={}, error={}",
                        emotion, sessionId, userId, e.getMessage(), e);
            }
        }*/

        //改用发送mq事件
        if(isNegativeEmotion(emotion)){
            try{
                NegativeEmotionEvent event = new NegativeEmotionEvent();
                event.setTenantId(tenantId);
                event.setUserId(userId);
                event.setSessionId(sessionId);
                event.setQuestion(question);
                event.setPriority("ANGRY".equalsIgnoreCase(emotion) ? "HIGH" : "MEDIUM");
                event.setEmotion(emotion);
                // 发送消息
                emotionEventPublisher.publishNegativeEmotion(event);
                log.info("[Chat] 负向情绪事件已发送到MQ: emotion={}, sessionId={}", emotion, sessionId);
            }catch (Exception e) {
                log.error("[Chat] 负向情绪事件发送失败: emotion={}, sessionId={}, error={}",
                        emotion, sessionId, e.getMessage(), e);
                // 降级：直接创建工单（保证业务不中断）
                try {
                    String priority = "ANGRY".equalsIgnoreCase(emotion) ? "HIGH" : "MEDIUM";
                    ticketService.createTicketOnNegative(tenantId, userId, sessionId, question, priority);
                } catch (Exception fallbackException) {
                    log.error("[Chat] 降级创建工单也失败", fallbackException);
                }
            }
        }

        
        // 8. 把 AI 回答写入 MySQL
        ChatMessage aiMsg = new ChatMessage();
        aiMsg.setTenantId(tenantId);
        aiMsg.setSessionId(sessionId);
        aiMsg.setSenderType("AI");
        aiMsg.setContent(aiReply);
        aiMsg.setEmotion(emotion);
        aiMsg.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(aiMsg);

        // 9. 更新 Redis 上下文
        appendContext(sessionId, "assistant", aiReply);

        // 业务级日志：便于后续做统计分析
        String questionPreview = question == null
                ? ""
                : (question.length() <= 50 ? question : question.substring(0, 50));
        int answerLen = aiReply == null ? 0 : aiReply.length();

        log.info("[Chat] tenantId={}, userId={}, sessionId={}, questionPreview={}, answerLen={}",
                tenantId, userId, sessionId, questionPreview, answerLen);
        // 10. 返回 AI 这一条消息（也可以同时返回用户这条）
        return aiMsg;


    }

    /**
     * 判断是否为负向情绪
     * @param emotion 情绪值（NEGATIVE, ANGRY, HAPPY, NEUTRAL等）
     * @return true表示负向情绪
     */
    private boolean isNegativeEmotion(String emotion) {
        if (emotion == null) {
            return false;
        }
        String e = emotion.toUpperCase();
        return "NEGATIVE".equals(e) || "ANGRY".equals(e);
    }

    //获取租户下所有会话
    @Override
    public List<ChatSession> listAllSessionsByTenant(Long tenantId) {
        return chatSessionMapper.selectList(
                Wrappers.<ChatSession>lambdaQuery()
                        .eq(ChatSession::getTenantId, tenantId)
                        .orderByDesc(ChatSession::getCreateTime)
        );
    }

    @Override
    public List<ChatSession> listUserSessionsByUsers(Long tenantId, Long userId) {
        return chatSessionMapper.selectList(
                Wrappers.<ChatSession>lambdaQuery()
                        .eq(ChatSession::getTenantId, tenantId)
                        .eq(ChatSession::getUserId, userId)
                        .orderByDesc(ChatSession::getCreateTime)
        );
    }

    @Override
    public void checkSessionOwnerOrAgent(Long tenantId, Long userId, Long sessionId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null || !tenantId.equals(session.getTenantId())) {
            throw new BizException(ErrorCodes.SESSION_NOT_FOUND, "会话不存在或不属于当前租户");
        }

        String role = SecurityUtil.currentRole();
        // 普通用户只能看自己的会话；客服/管理员可以看租户内所有会话
        if (Roles.isUser(role) && !userId.equals(session.getUserId())) {
            throw new BizException(ErrorCodes.FORBIDDEN, "无权访问该会话");
        }
    }

    //向redis存入context
    //每次有用户提问或 AI 回复时，把这条消息追加到 Redis List 里，保持最近 N 条
    private void appendContext(Long sessionId, String role, String content) {
        String key = buildContextKey(sessionId);
        Map<String, String> entry = new HashMap<>();
        entry.put("role", role);      // user / assistant
        entry.put("content", content);
        //转成 JSON 字符串，存入 Redis
        String json = JSONUtil.toJsonStr(entry);

        //leftPush 入 Redis List，放到队头，相当于“最新消息在最前面”。
        stringRedisTemplate.opsForList().leftPush(key, json);
        //trim 保持列表长度不超过 10
        //只保留前 N 条，自动丢弃更旧的上下文，防止列表无限增长
        stringRedisTemplate.opsForList().trim(key, 0, CONTEXT_LIMIT - 1);
        // 字符长度限制：按租户配置动态裁剪，丢弃最旧的消息
        Long tenantId = TenantContextHolder.getTenantId();
        trimContextByLength(sessionId, tenantId);
    }

    //构建redis key
    private String buildContextKey(Long sessionId) {
        return "chat:context:" + sessionId;
    }

    /**
     * 按租户决定上下文字符上限。
     * 这里只是示例：你可以以后改成从 DB / 配置表中查。
     */
    private int resolveTenantContextCharLimit(Long tenantId) {
        if (tenantId == null) {
            return DEFAULT_CONTEXT_CHAR_LIMIT;
        }
        // 示例：假设 tenantId < 1000 认为是“小租户”，>=1000 认为是“大租户”
        if (tenantId < 1000) {
            return SMALL_TENANT_CHAR_LIMIT;
        } else {
            return LARGE_TENANT_CHAR_LIMIT;
        }
    }

    /**
     * 按“总字符长度”裁剪 Redis 中的上下文：
     * - 保留最新的消息优先
     * - 一旦累积长度超过上限，就丢掉更旧的消息
     */
    private void trimContextByLength(Long sessionId, Long tenantId) {
        String key = buildContextKey(sessionId);
        // 先把当前这个会话的上下文全部取出来（你现在 leftPush，所以 index 0 是最新）
        List<String> jsonList = stringRedisTemplate.opsForList().range(key, 0, -1);
        if (CollectionUtils.isEmpty(jsonList)) {
            return;
        }

        int limit = resolveTenantContextCharLimit(tenantId);
        int total = 0;

        // kept 里从“最新”开始往后排，直到超过上限为止
        List<String> kept = new java.util.ArrayList<>();
        for (String json : jsonList) {
            Map<String, String> m = JSONUtil.toBean(json, new TypeReference<Map<String, String>>() {
            }, true);
            String content = m.getOrDefault("content", "");
            int len = content.length();
            if (total + len > limit) {
                break; // 超过上限，后面的更旧消息都不要了
            }
            kept.add(json);
            total += len;
        }

        // 用 kept 覆盖写回 Redis
        // 注意：当前 kept 是 [最新, 更旧, ...]，为了保持 leftPush 的“最新在前”顺序，
        // 这里用 leftPushAll + 反转写回
        stringRedisTemplate.delete(key);
        java.util.Collections.reverse(kept);
        stringRedisTemplate.opsForList().leftPushAll(key, kept);
    }

    /**
     *
     * 新增一层摘要
     */
    private void generateAndSaveSummaryIfNeeded(Long sessionId, Long tenantId) {
        // 1. 读取当前 context（listContextFromRedis）
        List<Map<String, String>> ctx = listContextFromRedis(sessionId);
        if (ctx.size() < 10) {
            return; // 太短不需要摘要
        }

        // 2. 构造一个简单的文本：role + content 拼在一起
        StringBuilder sb = new StringBuilder();
        for (Map<String, String> m : ctx) {
            sb.append(m.get("role")).append(": ").append(m.get("content")).append("\n");
        }

        // 3. 调用一个专门的 summarizer（可以通过 LLMClient 或单独 LangChain4j）
        String summary = llmClient.chat(
                Collections.emptyList(),
                "下面是用户和客服的一段对话，请用不超过 200 字总结当前会话的关键信息（用户是谁、在问什么、已给出哪些答案）：\n\n" +
                        sb.toString()
        );

        // 4. 写入 Redis: chat:summary:{sessionId}
        stringRedisTemplate.opsForValue().set("chat:summary:" + sessionId, summary);

        // 5. 可选：清理一部分旧 context，只保留最近几条
    }

}
