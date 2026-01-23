package com.ityfz.yulu.knowledge.controller;

import com.ityfz.yulu.common.model.ApiResponse;
import com.ityfz.yulu.common.security.SecurityUtil;
import com.ityfz.yulu.knowledge.dto.RetrievalResultDTO;
import com.ityfz.yulu.knowledge.service.KnowledgeSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "知识库-检索（Knowledge/Search）", description = "向量检索：基于 Qdrant 的 TopK 语义检索")
public class KnowledgeSearchController {

    private final KnowledgeSearchService knowledgeSearchService;

    @GetMapping("/search")
    @Operation(summary = "知识库检索", description = "输入 query 文本，返回 TopK 检索结果（支持 minScore 过滤）")
    public ApiResponse<List<RetrievalResultDTO>> search(
            @Parameter(description = "检索问题/关键词") @RequestParam("q") String q,
            @Parameter(description = "返回条数") @RequestParam(value = "topK", defaultValue = "10") Integer topK,
            @Parameter(description = "最小相似度阈值") @RequestParam(value = "minScore", defaultValue = "0.35") Double minScore
    ){
        Long tenantId = SecurityUtil.currentTenantId();
        return ApiResponse.success(knowledgeSearchService.search(tenantId, q, topK, minScore));
    }
}
