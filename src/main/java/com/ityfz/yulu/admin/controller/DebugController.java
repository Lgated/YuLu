package com.ityfz.yulu.admin.controller;

import com.ityfz.yulu.common.annotation.RequireRole;
import com.ityfz.yulu.common.model.ApiResponse;
import com.ityfz.yulu.common.security.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 调试接口（仅用于开发环境）
 * 用于快速修复客服负载等问题
 */
@RestController
@RequestMapping("/api/admin/debug")
@RequireRole("Agent")
@Tag(name = "调试接口", description = "仅用于开发调试，生产环境应禁用")
@RequiredArgsConstructor
public class DebugController {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    @PostMapping("/reset-agent-load/{agentId}")
    @Operation(summary = "重置客服负载", description = "将指定客服的当前会话数重置为0")
    public ApiResponse<String> resetAgentLoad(@PathVariable Long agentId) {
        Long tenantId = SecurityUtil.currentTenantId();
        
        // 构建 Redis key
        String key = "agent:status:" + tenantId + ":" + agentId;
        
        // 删除 Redis 中的状态
        Boolean deleted = redisTemplate.delete(key);
        
        if (Boolean.TRUE.equals(deleted)) {
            return ApiResponse.success("客服负载已重置，客服下次心跳时会自动重建状态");
        } else {
            return ApiResponse.success("客服状态不存在或已删除");
        }
    }
    
    @GetMapping("/agent-status/{agentId}")
    @Operation(summary = "查看客服状态", description = "查看指定客服的当前状态")
    public ApiResponse<Object> getAgentStatus(@PathVariable Long agentId) {
        Long tenantId = SecurityUtil.currentTenantId();
        
        // 构建 Redis key
        String key = "agent:status:" + tenantId + ":" + agentId;
        
        // 获取状态
        Object status = redisTemplate.opsForValue().get(key);
        
        if (status != null) {
            return ApiResponse.success("查询成功", status);
        } else {
            return ApiResponse.success("客服状态不存在", null);
        }
    }
    
    @DeleteMapping("/clear-queue")
    @Operation(summary = "清空排队队列", description = "清空指定租户的转人工排队队列")
    public ApiResponse<String> clearQueue() {
        Long tenantId = SecurityUtil.currentTenantId();
        
        // 构建 Redis key
        String key = "handoff:queue:" + tenantId;
        
        // 删除队列
        Boolean deleted = redisTemplate.delete(key);
        
        if (Boolean.TRUE.equals(deleted)) {
            return ApiResponse.success("排队队列已清空");
        } else {
            return ApiResponse.success("排队队列不存在或已清空");
        }
    }
    
    @GetMapping("/queue-info")
    @Operation(summary = "查看排队队列信息", description = "查看当前排队队列的详细信息")
    public ApiResponse<Object> getQueueInfo() {
        Long tenantId = SecurityUtil.currentTenantId();
        
        // 构建 Redis key
        String key = "handoff:queue:" + tenantId;
        
        // 获取队列长度
        Long length = redisTemplate.opsForList().size(key);
        
        // 获取队列内容
        Object queue = redisTemplate.opsForList().range(key, 0, -1);
        
        return ApiResponse.success("查询成功", new Object() {
            public final Long queueLength = length;
            public final Object queueContent = queue;
        });
    }
    
    @PostMapping("/force-reset-agent/{agentId}")
    @Operation(summary = "强制重置客服状态", description = "删除所有相关 Redis key 并重新初始化")
    public ApiResponse<String> forceResetAgent(@PathVariable Long agentId) {
        Long tenantId = SecurityUtil.currentTenantId();
        
        // 删除所有相关的 key
        String statusKey = "agent:status:" + tenantId + ":" + agentId;
        String sessionsKey = "agent:sessions:" + tenantId + ":" + agentId;
        String onlineSetKey = "agent:online:" + tenantId;
        
        redisTemplate.delete(statusKey);
        redisTemplate.delete(sessionsKey);
        redisTemplate.opsForZSet().remove(onlineSetKey, agentId.toString());
        
        return ApiResponse.success("客服状态已强制重置，请客服重新登录或刷新页面");
    }
    
    @GetMapping("/check-agent-redis/{agentId}")
    @Operation(summary = "检查客服 Redis 数据结构", description = "检查客服相关的所有 Redis key 和数据类型")
    public ApiResponse<Map<String, Object>> checkAgentRedis(@PathVariable Long agentId) {
        Long tenantId = SecurityUtil.currentTenantId();
        
        Map<String, Object> result = new HashMap<>();
        
        // 检查 status key
        String statusKey = "agent:status:" + tenantId + ":" + agentId;
        Boolean statusExists = redisTemplate.hasKey(statusKey);
        result.put("statusKeyExists", statusExists);
        
        if (Boolean.TRUE.equals(statusExists)) {
            // 检查数据类型
            org.springframework.data.redis.connection.DataType type = redisTemplate.type(statusKey);
            result.put("statusKeyType", type != null ? type.name() : "UNKNOWN");
            
            // 如果是 Hash，获取所有字段
            if (type == org.springframework.data.redis.connection.DataType.HASH) {
                Map<Object, Object> hash = redisTemplate.opsForHash().entries(statusKey);
                result.put("statusData", hash);
            } else {
                // 如果不是 Hash，获取原始值
                Object value = redisTemplate.opsForValue().get(statusKey);
                result.put("statusData", value);
                result.put("warning", "数据类型错误！应该是 HASH，实际是 " + type);
            }
        }
        
        // 检查 sessions key
        String sessionsKey = "agent:sessions:" + tenantId + ":" + agentId;
        Boolean sessionsExists = redisTemplate.hasKey(sessionsKey);
        result.put("sessionsKeyExists", sessionsExists);
        
        if (Boolean.TRUE.equals(sessionsExists)) {
            Object sessionsValue = redisTemplate.opsForValue().get(sessionsKey);
            result.put("sessionsData", sessionsValue);
        }
        
        // 检查在线集合
        String onlineSetKey = "agent:online:" + tenantId;
        Double score = redisTemplate.opsForZSet().score(onlineSetKey, agentId.toString());
        result.put("inOnlineSet", score != null);
        result.put("onlineScore", score);
        
        return ApiResponse.success("检查完成", result);
    }
}
