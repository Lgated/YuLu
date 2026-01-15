-- ============================================
-- YuLu 智链客服中台 - 数据库初始化脚本
-- ============================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS `yulu` 
DEFAULT CHARACTER SET utf8mb4 
DEFAULT COLLATE utf8mb4_unicode_ci;

USE `yulu`;

-- ============================================
-- 1. 租户表
-- ============================================
DROP TABLE IF EXISTS `tenant`;
CREATE TABLE `tenant` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_code` VARCHAR(50) NOT NULL COMMENT '租户编码（唯一）',
  `name` VARCHAR(100) NOT NULL COMMENT '租户名称',
  `status` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户表';

-- ============================================
-- 2. 用户表
-- ============================================
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
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
DROP TABLE IF EXISTS `chat_session`;
CREATE TABLE `chat_session` (
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
DROP TABLE IF EXISTS `chat_message`;
CREATE TABLE `chat_message` (
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
DROP TABLE IF EXISTS `ticket`;
CREATE TABLE `ticket` (
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
DROP TABLE IF EXISTS `ticket_comment`;
CREATE TABLE `ticket_comment` (
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
DROP TABLE IF EXISTS `notify_message`;
CREATE TABLE `notify_message` (
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
-- 初始化完成
-- ============================================
SELECT '数据库初始化完成！' AS message;


