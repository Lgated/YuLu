package com.ityfz.yulu.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档实体
 */
@Data
@TableName("knowledge_document")
public class Document {

    /**
     * 文档ID（主键）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 文档标题
     */
    private String title;

    /**
     * 文档内容（纯文本）
     */
    private String content;

    /**
     * 来源（用户上传/FAQ/产品手册/帮助文档等）
     */
    private String source;

    /**
     * 文件类型（txt/pdf/docx/md等）
     */
    private String fileType;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 状态：0-未索引 1-已索引 2-索引失败
     */
    private Integer status;

    /**
     * 索引时间
     */
    private LocalDateTime indexedAt;

    /**
     * 创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT) //自动填充创建时间
    private LocalDateTime createTime;

    /**
     * 更新时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT_UPDATE) // 自动填充更新时间
    private LocalDateTime updateTime;


}
