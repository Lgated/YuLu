package com.ityfz.yulu.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ityfz.yulu.ticket.entity.Ticket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 工单Mapper接口
 */
@Mapper
public interface TicketMapper extends BaseMapper<Ticket> {


    @Select("SELECT status, COUNT(*) as cnt FROM yulu.ticket WHERE tenant_id = #{tenantId} GROUP BY status")
    List<Map<String,Object>> countByStatus(@Param("tenantId") Long tenantId);

    @Select("SELECT priority, COUNT(*) as cnt FROM yulu.ticket WHERE tenant_id = #{tenantId} GROUP BY priority")
    List<Map<String,Object>> countByPriority(@Param("tenantId") Long tenantId);

}
