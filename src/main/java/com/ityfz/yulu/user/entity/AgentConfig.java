package com.ityfz.yulu.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_config")
public class AgentConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long userId; // 客服用户ID

    private Integer maxConcurrentSessions; // 最大并发会话数

    private String workSchedule; // JSON格式的工作时段

    private String skillTags; // 技能标签，逗号分隔

    private Integer autoAccept; // 是否自动接入

    private String responseTemplate; // JSON格式的快捷回复模板

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

