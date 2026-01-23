package com.ityfz.yulu.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ityfz.yulu.user.entity.AgentConfig;
import com.ityfz.yulu.user.mapper.AgentConfigMapper;
import com.ityfz.yulu.user.service.AgentConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Slf4j
@Service
public class AgentConfigServiceImpl extends ServiceImpl<AgentConfigMapper, AgentConfig> implements AgentConfigService {



    @Override
    @Transactional
    public AgentConfig getOrCreateConfig(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) {
            throw new IllegalArgumentException("租户ID和用户ID不能为空");
        }

        // 查询现有配置
        AgentConfig config = this.getOne(new LambdaQueryWrapper<AgentConfig>()
                .eq(AgentConfig::getTenantId, tenantId)
                .eq(AgentConfig::getUserId, userId));

        if (config != null) {
            return config;
        }

        // 不存在则创建默认配置
        config = new AgentConfig();
        config.setTenantId(tenantId);
        config.setUserId(userId);
        config.setMaxConcurrentSessions(5); // 默认最大并发5个会话
        config.setAutoAccept(0); // 默认不自动接入
        config.setCreateTime(LocalDateTime.now());
        config.setUpdateTime(LocalDateTime.now());

        this.save(config);

        log.info("[AgentConfig] 创建默认配置: tenantId={}, userId={}", tenantId, userId);

        return config;
    }

    @Override
    @Transactional
    public void updateConfig(Long tenantId, Long userId, AgentConfig config) {
        if (tenantId == null || userId == null) {
            throw new IllegalArgumentException("租户ID和用户ID不能为空");
        }

        // 查询现有配置
        AgentConfig existing = getOrCreateConfig(tenantId, userId);

        // 更新字段
        if (config.getMaxConcurrentSessions() != null) {
            existing.setMaxConcurrentSessions(config.getMaxConcurrentSessions());
        }
        if (config.getWorkSchedule() != null) {
            existing.setWorkSchedule(config.getWorkSchedule());
        }
        if (config.getSkillTags() != null) {
            existing.setSkillTags(config.getSkillTags());
        }
        if (config.getAutoAccept() != null) {
            existing.setAutoAccept(config.getAutoAccept());
        }
        if (config.getResponseTemplate() != null) {
            existing.setResponseTemplate(config.getResponseTemplate());
        }

        existing.setUpdateTime(LocalDateTime.now());
        this.updateById(existing);

        log.info("[AgentConfig] 更新配置: tenantId={}, userId={}", tenantId, userId);
    }

    @Override
    public Integer getMaxConcurrentSessions(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) {
            return 5; // 默认值
        }

        AgentConfig config = getOrCreateConfig(tenantId, userId);
        return config.getMaxConcurrentSessions() != null
                ? config.getMaxConcurrentSessions()
                : 5; // 默认值
    }
}
