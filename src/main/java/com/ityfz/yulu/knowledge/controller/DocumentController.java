package com.ityfz.yulu.knowledge.controller;

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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/document")
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
    public ApiResponse<Long> upload(@RequestPart("file") MultipartFile file,
                                    @RequestParam(value = "title", required = false) String title,
                                    @RequestParam(value = "source", required = false) String source) {
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
    public ApiResponse<List<DocumentListItemResponse>> list(
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
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
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        Long tenantId = SecurityUtil.currentTenantId();
        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "缺少租户信息，请先登录");
        }
        documentService.deleteDocument(id, tenantId);
        return ApiResponse.success();
    }

    @GetMapping("/file/{id}")
    public ApiResponse<List<Chunk>> detailByFile(@PathVariable("id") Long documentId) {
        Long tenantId = SecurityUtil.currentTenantId();
        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "缺少租户信息，请先登录");
        }
        List<Chunk> chunksByDocumentId = chunkService.getChunksByDocumentId(documentId);
        return ApiResponse.success(chunksByDocumentId);
    }

}
