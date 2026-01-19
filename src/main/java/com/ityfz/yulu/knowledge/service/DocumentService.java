package com.ityfz.yulu.knowledge.service;

import com.ityfz.yulu.knowledge.entity.Document;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档管理服务接口
 */
public interface DocumentService {


    /**
     * 上传文档（支持文件上传和文本内容）
     *
     * @param tenantId 租户ID
     * @param title 文档标题
     * @param file 上传的文件（可选）
     * @param content 文本内容（如果 file 为空，则使用 content）
     * @param source 文档来源（用户上传/FAQ/产品手册等）
     * @return 文档ID
     */
    Long uploadDocument(Long tenantId, String title, MultipartFile file, String content, String source);

    /**
     * 解析文档（提取纯文本）
     *
     * @param fileBytes 文件字节数组
     * @param fileType 文件类型（txt/pdf/docx/md等）
     * @return 解析后的纯文本
     */
    String parseDocument(byte[] fileBytes, String fileType);

    /**
     * 获取文档列表（分页）
     *
     * @param tenantId 租户ID
     * @param pageNum 页码（从1开始）
     * @param pageSize 每页大小
     * @return 文档列表
     */
    List<Document> listDocuments(Long tenantId, Integer pageNum, Integer pageSize);

    /**
     * 获取文档详情
     *
     * @param documentId 文档ID
     * @param tenantId 租户ID（用于权限校验）
     * @return 文档详情
     */
    Document getDocument(Long documentId, Long tenantId);

    /**
     * 删除文档（同时删除关联的 Chunk）
     *
     * @param documentId 文档ID
     * @param tenantId 租户ID（用于权限校验）
     */
    void deleteDocument(Long documentId, Long tenantId);

    /**
     * 更新文档状态
     *
     * @param documentId 文档ID
     * @param status 状态（0-未索引 1-已索引 2-索引失败）
     */
    void updateDocumentStatus(Long documentId, Integer status);
}
