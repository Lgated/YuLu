package com.ityfz.yulu.knowledge.controller;

import com.ityfz.yulu.common.model.ApiResponse;
import com.ityfz.yulu.common.security.SecurityUtil;
import com.ityfz.yulu.knowledge.dto.RetrievalResultDTO;
import com.ityfz.yulu.knowledge.service.KnowledgeSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 检索接口
 */
@RestController
@RequestMapping("/api/admin/knowledge")
@RequiredArgsConstructor
public class KnowledgeSearchController {

    private final KnowledgeSearchService knowledgeSearchService;

    @GetMapping("/search")
    public ApiResponse<List<RetrievalResultDTO>> search(
            @RequestParam("q") String q,
            @RequestParam(value = "topK", defaultValue = "10") Integer topK,
            @RequestParam(value = "minScore", defaultValue = "0.35") Double minScore
    ){
        Long tenantId = SecurityUtil.currentTenantId();
        return ApiResponse.success(knowledgeSearchService.search(tenantId, q, topK, minScore));
    }
}
