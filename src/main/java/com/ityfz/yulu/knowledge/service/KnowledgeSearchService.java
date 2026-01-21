package com.ityfz.yulu.knowledge.service;

import com.ityfz.yulu.knowledge.dto.RetrievalResultDTO;

import java.util.List;

public interface KnowledgeSearchService {

    // 检索
    List<RetrievalResultDTO> search(Long tenantId, String query, int topK, double minScore);
}
