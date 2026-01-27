-- ============================================
-- YuLu 智链客服中台 - 数据库初始化脚本
-- ============================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS `yulu` 
DEFAULT CHARACTER SET utf8mb4 
DEFAULT COLLATE utf8mb4_unicode_ci;

-- 注意：请先手动选择数据库 yulu，或者使用 yulu.表名 的方式
-- 如果您的SQL客户端不支持USE语句，请手动选择数据库后再执行下面的语句

-- ============================================
-- 1. 租户表
-- ============================================
DROP TABLE IF EXISTS `yulu`.`tenant`;
CREATE TABLE `yulu`.`tenant` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_code` VARCHAR(50) NOT NULL COMMENT '租户编码（唯一）',
  `name` VARCHAR(100) NOT NULL COMMENT '租户名称',
  `status` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `tenant_identifier` VARCHAR(50) DEFAULT NULL COMMENT '租户标识码（对外使用，比tenantCode更友好）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_code`),
  UNIQUE KEY `uk_tenant_identifier` (`tenant_identifier`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户表';

-- ============================================
-- 2. 用户表
-- ============================================
DROP TABLE IF EXISTS `yulu`.`user`;
CREATE TABLE `yulu`.`user` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` BIGINT(20) NOT NULL COMMENT '租户ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `password` VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
  `role` VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN-管理员，AGENT-客服，USER-普通用户',
  `status` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
  `nick_name` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
  `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_username` (`tenant_id`, `username`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_status` (`status`),
  KEY `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ============================================
-- 3. 会话表
-- ============================================
DROP TABLE IF EXISTS `yulu`.`chat_session`;
CREATE TABLE `yulu`.`chat_session` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` BIGINT(20) NOT NULL COMMENT '租户ID',
  `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
  `session_title` VARCHAR(200) DEFAULT NULL COMMENT '会话标题',
  `status` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '状态：1-进行中，0-已结束',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_tenant_user_status` (`tenant_id`, `user_id`, `status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表';

-- ============================================
-- 4. 消息表
-- ============================================
DROP TABLE IF EXISTS `yulu`.`chat_message`;
CREATE TABLE `yulu`.`chat_message` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` BIGINT(20) NOT NULL COMMENT '租户ID',
  `session_id` BIGINT(20) NOT NULL COMMENT '会话ID',
  `sender_type` VARCHAR(20) NOT NULL COMMENT '发送者类型：USER-用户，AI-AI，AGENT-客服',
  `content` TEXT NOT NULL COMMENT '消息内容',
  `emotion` VARCHAR(20) DEFAULT 'NORMAL' COMMENT '情绪标签：NORMAL-正常，NEGATIVE-负面，POSITIVE-正面，ANGRY-愤怒',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_tenant_session` (`tenant_id`, `session_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表';

-- ============================================
-- 5. 工单表
-- ============================================
DROP TABLE IF EXISTS `yulu`.`ticket`;
CREATE TABLE `yulu`.`ticket` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` BIGINT(20) NOT NULL COMMENT '租户ID',
  `user_id` BIGINT(20) NOT NULL COMMENT '用户ID（创建工单的用户）',
  `session_id` BIGINT(20) DEFAULT NULL COMMENT '关联的会话ID',
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '工单状态：PENDING-待处理，PROCESSING-处理中，DONE-已完成，CLOSED-已关闭',
  `priority` VARCHAR(20) NOT NULL DEFAULT 'MEDIUM' COMMENT '优先级：LOW-低，MEDIUM-中，HIGH-高，URGENT-紧急',
  `assignee` BIGINT(20) DEFAULT NULL COMMENT '分配给的用户ID（客服或管理员）',
  `title` VARCHAR(200) NOT NULL COMMENT '工单标题',
  `description` TEXT COMMENT '工单描述（用户的问题内容）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_assignee` (`assignee`),
  KEY `idx_status` (`status`),
  KEY `idx_priority` (`priority`),
  KEY `idx_tenant_status` (`tenant_id`, `status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单表';

-- ============================================
-- 6. 工单备注表
-- ============================================
DROP TABLE IF EXISTS `yulu`.`ticket_comment`;
CREATE TABLE `yulu`.`ticket_comment` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `ticket_id` BIGINT(20) NOT NULL COMMENT '工单ID',
  `tenant_id` BIGINT(20) NOT NULL COMMENT '租户ID',
  `user_id` BIGINT(20) NOT NULL COMMENT '操作人ID（谁添加的这条跟进记录）',
  `content` TEXT NOT NULL COMMENT '跟进内容',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_ticket_id` (`ticket_id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_tenant_ticket` (`tenant_id`, `ticket_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单备注表';

-- ============================================
-- 7. 通知消息表
-- ============================================
DROP TABLE IF EXISTS `yulu`.`notify_message`;
CREATE TABLE `yulu`.`notify_message` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` BIGINT(20) NOT NULL COMMENT '租户ID',
  `user_id` BIGINT(20) NOT NULL COMMENT '收件人ID',
  `type` VARCHAR(50) NOT NULL COMMENT '通知类型：TICKET_ASSIGNED-工单分配等',
  `title` VARCHAR(200) NOT NULL COMMENT '通知标题',
  `content` TEXT COMMENT '通知内容',
  `read_flag` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '已读标志：0-未读，1-已读',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_read_flag` (`read_flag`),
  KEY `idx_tenant_user_read` (`tenant_id`, `user_id`, `read_flag`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知消息表';

-- ============================================
-- 8. 客服配置表
-- ============================================
DROP TABLE IF EXISTS `yulu`.`agent_config`;
CREATE TABLE `yulu`.`agent_config` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` BIGINT(20) NOT NULL COMMENT '租户ID',
  `user_id` BIGINT(20) NOT NULL COMMENT '客服用户ID',
  `max_concurrent_sessions` INT(11) DEFAULT NULL COMMENT '最大并发会话数',
  `work_schedule` TEXT COMMENT '工作时段（JSON格式）',
  `skill_tags` VARCHAR(500) DEFAULT NULL COMMENT '技能标签，逗号分隔',
  `auto_accept` TINYINT(1) DEFAULT 0 COMMENT '是否自动接入：0-否，1-是',
  `response_template` TEXT COMMENT '快捷回复模板（JSON格式）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_user_id` (`user_id`),
  UNIQUE KEY `uk_tenant_user` (`tenant_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客服配置表';

-- ============================================
-- 9. 知识库文档表
-- ============================================
DROP TABLE IF EXISTS `yulu`.`knowledge_document`;
CREATE TABLE `yulu`.`knowledge_document` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '文档ID（主键）',
  `tenant_id` BIGINT(20) NOT NULL COMMENT '租户ID',
  `title` VARCHAR(500) NOT NULL COMMENT '文档标题',
  `content` LONGTEXT COMMENT '文档内容（纯文本）',
  `source` VARCHAR(100) DEFAULT NULL COMMENT '来源（用户上传/FAQ/产品手册/帮助文档等）',
  `file_type` VARCHAR(50) DEFAULT NULL COMMENT '文件类型（txt/pdf/docx/md等）',
  `file_size` BIGINT(20) DEFAULT NULL COMMENT '文件大小（字节）',
  `status` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '状态：0-未索引，1-已索引，2-索引失败',
  `indexed_at` DATETIME DEFAULT NULL COMMENT '索引时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_status` (`status`),
  KEY `idx_tenant_status` (`tenant_id`, `status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档表';

-- ============================================
-- 10. 知识库文档切分表
-- ============================================
DROP TABLE IF EXISTS `yulu`.`knowledge_chunk`;
CREATE TABLE `yulu`.`knowledge_chunk` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT 'Chunk ID（主键）',
  `document_id` BIGINT(20) NOT NULL COMMENT '文档ID',
  `tenant_id` BIGINT(20) NOT NULL COMMENT '租户ID（冗余字段，便于查询）',
  `chunk_index` INT(11) NOT NULL COMMENT '在文档中的序号（从0开始）',
  `content` TEXT NOT NULL COMMENT 'Chunk 内容',
  `content_length` INT(11) DEFAULT NULL COMMENT '内容长度（字符数）',
  `qdrant_point_id` BIGINT(20) DEFAULT NULL COMMENT 'Qdrant 中的 Point ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_document_id` (`document_id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_tenant_document` (`tenant_id`, `document_id`),
  KEY `idx_qdrant_point_id` (`qdrant_point_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档切分表';

-- ============================================
-- 初始化完成
-- ============================================
SELECT '数据库初始化完成！' AS message;














