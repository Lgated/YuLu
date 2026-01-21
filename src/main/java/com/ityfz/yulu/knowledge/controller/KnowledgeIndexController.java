package com.ityfz.yulu.knowledge.controller;

import com.ityfz.yulu.common.model.ApiResponse;
import com.ityfz.yulu.common.security.SecurityUtil;
import com.ityfz.yulu.knowledge.service.ChunkIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * 索引接口
 */
@RestController
@RequestMapping("/api/admin/knowledge")
@RequiredArgsConstructor
public class KnowledgeIndexController {

    private final ChunkIndexService chunkIndexService;

    @PostMapping("/document/{documentId}/index")
    public ApiResponse<Void> index(@PathVariable Long documentId) {
        Long tenantId = SecurityUtil.currentTenantId();
        chunkIndexService.indexDocument(tenantId, documentId);
        return ApiResponse.success();
    }

}
