package com.ityfz.yulu.knowledge.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ityfz.yulu.common.enums.ErrorCodes;
import com.ityfz.yulu.common.exception.BizException;
import com.ityfz.yulu.knowledge.entity.Document;
import com.ityfz.yulu.knowledge.mapper.ChunkMapper;
import com.ityfz.yulu.knowledge.mapper.DocumentMapper;
import com.ityfz.yulu.knowledge.service.ChunkService;
import com.ityfz.yulu.knowledge.service.DocumentService;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class DocumentServiceImpl implements DocumentService {

    private final DocumentMapper documentMapper;
    private final ChunkService chunkService;
    private final TikaDocumentParser tikaDocumentParser;

    public DocumentServiceImpl(DocumentMapper documentMapper,
                               ChunkService chunkService,
                               TikaDocumentParser tikaDocumentParser){
        this.documentMapper = documentMapper;
        this.chunkService = chunkService;
        this.tikaDocumentParser = tikaDocumentParser;
    }

    // 从配置文件读取
    @Value("${rag.chunk.size:500}")
    private int chunkSize;

    @Value("${rag.chunk.overlap:50}")
    private int overlapSize;

    @Value("${rag.document.max-size:10485760}")  // 默认 10MB
    private long maxFileSize;

    @Override
    @Transactional
    public Long uploadDocument(Long tenantId, String title, MultipartFile file, String content, String source) {
        // 1. 参数校验
        if (tenantId == null) {
            throw new BizException(ErrorCodes.VALIDATION_ERROR, "租户ID不能为空");
        }
        if (title == null || title.trim().isEmpty()) {
            throw new BizException(ErrorCodes.VALIDATION_ERROR, "文档标题不能为空");
        }

        String parsedContent;
        String fileType = null;
        Long fileSize = null;
        // 2. 解析文档内容`
        if (file != null && !file.isEmpty()) {
            // 从文件解析
            try {
                // 检查文件大小
                if (file.getSize() > maxFileSize) {
                    throw new BizException(ErrorCodes.VALIDATION_ERROR,
                            "文件大小超过限制: " + (maxFileSize / 1024 / 1024) + "MB");
                }

                //获取文件类型
                fileType = getFileType(file.getOriginalFilename());
                fileSize = file.getSize();
                //解析文件
                parsedContent = parseDocument(file.getBytes(), fileType);

                // 如果标题为空，使用文件名
                if (title == null || title.trim().isEmpty()) {
                    title = file.getOriginalFilename();
                }
            } catch (IOException e) {
                log.error("[DocumentService] 文件读取失败", e);
                throw new BizException(ErrorCodes.SYSTEM_ERROR, "文件读取失败: " + e.getMessage());
            }
        } else if (content != null && !content.trim().isEmpty()) {
            // 直接使用文本内容
            parsedContent = content.trim();
            fileType = "text";
            fileSize = (long) parsedContent.getBytes(StandardCharsets.UTF_8).length;
        } else {
            throw new BizException(ErrorCodes.VALIDATION_ERROR, "文件或内容不能同时为空");
        }

        // 3. 保存文档到数据库
        Document document = new Document();
        document.setTenantId(tenantId);
        document.setTitle(title);
        document.setContent(parsedContent);
        document.setSource(source != null ? source : "用户上传");
        document.setFileType(fileType);
        document.setFileSize(fileSize);
        document.setStatus(0);  // 0-未索引
        document.setCreateTime(LocalDateTime.now());
        document.setUpdateTime(LocalDateTime.now());

        documentMapper.insert(document);
        Long documentId = document.getId();

        log.info("[DocumentService] 文档上传成功: documentId={}, tenantId={}, title={}, contentLength={}",
                documentId, tenantId, title, parsedContent.length());

        // 4. 切分文档并保存 Chunk
        try {
            chunkService.chunkAndSave(documentId, tenantId, parsedContent, chunkSize, overlapSize);
            log.info("[DocumentService] 文档切分完成: documentId={}", documentId);
        } catch (Exception e) {
            log.error("[DocumentService] 文档切分失败: documentId={}", documentId, e);
            // 切分失败不影响文档保存，但记录错误
            document.setStatus(2);  // 2-索引失败
            documentMapper.updateById(document);
        }

        return documentId;
    }

    @Override
    public String parseDocument(byte[] fileBytes, String fileType) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new BizException(ErrorCodes.VALIDATION_ERROR, "文件内容为空");
        }
        // fileType 可以传原始文件名或扩展名，Tika 会结合内容自动判别
        return tikaDocumentParser.parse(fileBytes, fileType);
    }

    @Override
    public List<Document> listDocuments(Long tenantId, Integer pageNum, Integer pageSize) {
        if (tenantId == null) {
            throw new BizException(ErrorCodes.VALIDATION_ERROR, "租户ID不能为空");
        }

        // 设置默认值
        if (pageNum == null || pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = 10;
        }
        if (pageSize > 100) {
            pageSize = 100;  // 限制最大每页数量
        }

        // 分页查询
        Page<Document> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Document> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Document::getTenantId, tenantId)
                .orderByDesc(Document::getCreateTime);

        Page<Document> result = documentMapper.selectPage(page, queryWrapper);
        return result.getRecords();
    }

    @Override
    public Document getDocument(Long documentId, Long tenantId) {
        if (documentId == null) {
            throw new BizException(ErrorCodes.VALIDATION_ERROR, "文档ID不能为空");
        }
        if (tenantId == null) {
            throw new BizException(ErrorCodes.VALIDATION_ERROR, "租户ID不能为空");
        }

        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BizException(ErrorCodes.NOT_FOUND, "文档不存在");
        }

        // 权限校验：只能查看自己租户的文档
        if (!document.getTenantId().equals(tenantId)) {
            throw new BizException(ErrorCodes.FORBIDDEN, "无权访问该文档");
        }

        return document;
    }

    @Override
    public void deleteDocument(Long documentId, Long tenantId) {
        // 1. 权限校验
        Document document = getDocument(documentId, tenantId);

        // 2. 删除关联的 Chunk
        chunkService.deleteChunksByDocumentId(documentId);

        // 3. 删除文档
        documentMapper.deleteById(documentId);

        log.info("[DocumentService] 文档删除成功: documentId={}, tenantId={}", documentId, tenantId);

        // TODO: 后续在索引服务中删除 Qdrant 中的向量
    }

    @Override
    public void updateDocumentStatus(Long documentId, Integer status) {
        Document document = new Document();
        document.setId(documentId);
        document.setStatus(status);
        if (status == 1) {  // 已索引
            document.setIndexedAt(LocalDateTime.now());
        }
        document.setUpdateTime(LocalDateTime.now());
        documentMapper.updateById(document);
    }


    /**
     * 根据文件名获取文件类型
     */
    private String getFileType(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "txt";
        }

        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return "txt";
        }

        return filename.substring(lastDotIndex + 1).toLowerCase();
    }
}
