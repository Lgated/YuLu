package com.ityfz.yulu.knowledge.controller;

import com.ityfz.yulu.common.model.ApiResponse;
import com.ityfz.yulu.common.security.SecurityUtil;
import com.ityfz.yulu.knowledge.service.ChunkIndexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "知识库-索引（Knowledge/Index）", description = "索引：对文档进行 chunk 切分、向量化并写入 Qdrant")
public class KnowledgeIndexController {

    private final ChunkIndexService chunkIndexService;

    @PostMapping("/document/{documentId}/index")
    @Operation(summary = "索引文档", description = "对指定文档进行 chunk 切分、向量化、写入 Qdrant，并更新文档索引状态")
    public ApiResponse<Void> index(@PathVariable Long documentId) {
        Long tenantId = SecurityUtil.currentTenantId();
        chunkIndexService.indexDocument(tenantId, documentId);
        return ApiResponse.success();
    }

}
