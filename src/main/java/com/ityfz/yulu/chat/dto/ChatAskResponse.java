package com.ityfz.yulu.chat.dto;

import com.ityfz.yulu.chat.entity.ChatMessage;
import com.ityfz.yulu.knowledge.dto.RagRefDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 客服对话 ask 接口响应：AI 消息 + 本轮的 RAG 引用（若有）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatAskResponse {

    private ChatMessage aiMessage;
    private List<RagRefDTO> refs;
}







