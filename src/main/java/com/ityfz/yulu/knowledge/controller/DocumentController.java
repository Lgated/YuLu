package com.ityfz.yulu.knowledge.controller;

import com.ityfz.yulu.common.annotation.RequireRole;
import com.ityfz.yulu.common.enums.ErrorCodes;
import com.ityfz.yulu.common.exception.BizException;
import com.ityfz.yulu.common.model.ApiResponse;
import com.ityfz.yulu.common.security.SecurityUtil;
import com.ityfz.yulu.knowledge.dto.DocumentDetailResponse;
import com.ityfz.yulu.knowledge.dto.DocumentListItemResponse;
import com.ityfz.yulu.knowledge.entity.Chunk;
import com.ityfz.yulu.knowledge.entity.Document;
import com.ityfz.yulu.knowledge.service.ChunkService;
import com.ityfz.yulu.knowledge.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/document")
@Tag(name = "知识库-文档（Knowledge/Document）", description = "文档上传、列表、详情、删除（管理员为主；只读接口可开放给客服）")

public class DocumentController {

    private final DocumentService documentService;
    private final ChunkService chunkService;

    public DocumentController (DocumentService documentService,
                               ChunkService chunkService){
        this.documentService = documentService;
        this.chunkService = chunkService;
    }

    /**
     * 上传文件
     */
    // 就是告诉 Spring：只收‘带文件上传的表单’
    @PostMapping(value = "/upload" , consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireRole("ADMIN")
    @Operation(summary = "上传知识库文档", description = "上传文档并落库（随后可调用索引接口进行 chunk 切分与向量化）")
    public ApiResponse<Long> upload(@RequestPart("file") MultipartFile file,
                                    @Parameter(description = "文档标题（可选）") @RequestParam(value = "title", required = false) String title,
                                    @Parameter(description = "来源（可选）") @RequestParam(value = "source", required = false) String source) {
        Long tenantId = SecurityUtil.currentTenantId();
        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "缺少租户信息，请先登录");
        }
        Long docId = documentService.uploadDocument(tenantId, title, file, null, source);
        return ApiResponse.success(docId);
    }


    /**
     * 分页查询文档列表
     */
    @GetMapping("/list")
    @RequireRole({"ADMIN","AGENT"})
    @Operation(summary = "查询文档列表", description = "按租户查询文档列表。当前实现返回 List（非 IPage），pageNum/pageSize 由 service 内部裁剪")
    public ApiResponse<List<DocumentListItemResponse>> list(
            @Parameter(description = "页码（可选）") @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @Parameter(description = "每页大小（可选）") @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        Long tenantId = SecurityUtil.currentTenantId();
        List<Document> docs = documentService.listDocuments(tenantId, pageNum, pageSize);
        List<DocumentListItemResponse> resp = docs.stream().map(d -> {
            DocumentListItemResponse r = new DocumentListItemResponse();
            r.setId(d.getId());
            r.setTitle(d.getTitle());
            r.setSource(d.getSource());
            r.setFileType(d.getFileType());
            r.setFileSize(d.getFileSize());
            r.setStatus(d.getStatus());
            r.setCreateTime(d.getCreateTime());
            return r;
        }).toList();
        return ApiResponse.success(resp);
    }

    /**
     * 获取文档详情
     */
    @GetMapping("/{id}")
    @RequireRole({"ADMIN","AGENT"})
    @Operation(summary = "查询文档详情", description = "返回文档元数据 + contentPreview（内容截断预览）")
    public ApiResponse<DocumentDetailResponse> detail(@PathVariable("id") Long id) {
        Long tenantId = SecurityUtil.currentTenantId();
        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "缺少租户信息，请先登录");
        }
        Document d = documentService.getDocument(id, tenantId);
        DocumentDetailResponse r = new DocumentDetailResponse();
        r.setId(d.getId());
        r.setTitle(d.getTitle());
        r.setSource(d.getSource());
        r.setFileType(d.getFileType());
        r.setFileSize(d.getFileSize());
        r.setStatus(d.getStatus());
        r.setIndexedAt(d.getIndexedAt());
        r.setCreateTime(d.getCreateTime());
        r.setUpdateTime(d.getUpdateTime());
        String content = d.getContent();
        // 文档内容过长 进行截取处理
        if (content != null && content.length() > 1000) {
            r.setContentPreview(content.substring(0, 1000) + "...");
        } else {
            r.setContentPreview(content);
        }
        return ApiResponse.success(r);
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/{id}")
    @RequireRole("ADMIN")
    @Operation(summary = "删除文档", description = "删除文档（以及相关数据，按后端 service 实现）")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        Long tenantId = SecurityUtil.currentTenantId();
        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "缺少租户信息，请先登录");
        }
        documentService.deleteDocument(id, tenantId);
        return ApiResponse.success();
    }

    /**
     *  根据文档ID获取所有 Chunk
     */
    @GetMapping("/file/{id}")
    @RequireRole({"ADMIN","AGENT"})
    public ApiResponse<List<Chunk>> detailByFile(@PathVariable("id") Long documentId) {
        Long tenantId = SecurityUtil.currentTenantId();
        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "缺少租户信息，请先登录");
        }
        List<Chunk> chunksByDocumentId = chunkService.getChunksByDocumentId(documentId);
        return ApiResponse.success(chunksByDocumentId);
    }

}
