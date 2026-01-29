package com.ityfz.yulu.handoff.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ityfz.yulu.handoff.entity.HandoffRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 转人工请求Mapper接口
 */
@Mapper
public interface HandoffRequestMapper extends BaseMapper<HandoffRequest> {

    /**
     * 查询指定会话的未完成转人工请求
     */
    @Select("SELECT * FROM handoff_request WHERE session_id = #{sessionId} " +
            "AND status NOT IN ('COMPLETED', 'CLOSED') ORDER BY create_time DESC LIMIT 1")
    HandoffRequest selectUncompletedBySessionId(@Param("sessionId") Long sessionId);

    /**
     * 查询指定客服的待处理转人工请求
     */
    @Select("SELECT * FROM handoff_request WHERE tenant_id = #{tenantId} " +
            "AND agent_id = #{agentId} AND status = 'ASSIGNED' ORDER BY create_time ASC")
    List<HandoffRequest> selectPendingByAgentId(@Param("tenantId") Long tenantId,
                                                @Param("agentId") Long agentId);

    /**
     * 查询指定租户的排队中请求数量
     */
    @Select("SELECT COUNT(*) FROM handoff_request WHERE tenant_id = #{tenantId} " +
            "AND status = 'PENDING'")
    Long countPendingByTenantId(@Param("tenantId") Long tenantId);
}
