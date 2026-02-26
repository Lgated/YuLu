package com.ityfz.yulu.admin.mapper;

import com.ityfz.yulu.admin.vo.DashboardTrendPointVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;


import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AdminDashboardMapper {

    @Select("""
            SELECT COUNT(1)
            FROM chat_session
            WHERE tenant_id = #{tenantId}
              AND create_time >= CURDATE()
            """)
    Long countTodaySession(@Param("tenantId") Long tenantId);


    @Select("""
            SELECT COUNT(1)
            FROM handoff_request
            WHERE tenant_id = #{tenantId}
              AND create_time >= CURDATE()
            """)
    Long countTodayHandoff(@Param("tenantId") Long tenantId);

    @Select("""
            SELECT COUNT(1)
            FROM ticket
            WHERE tenant_id = #{tenantId}
              AND status IN ('PENDING','PROCESSING')
            """)
    Long countPendingTicket(@Param("tenantId") Long tenantId);

    @Select("""
            SELECT
                DATE_FORMAT(create_time, '%Y-%m-%d') AS date,
                COUNT(1) AS sessionCount
            FROM chat_session
            WHERE tenant_id = #{tenantId}
              AND create_time >= #{startDate}
            GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d')
            ORDER BY DATE_FORMAT(create_time, '%Y-%m-%d')
            """)
    List<DashboardTrendPointVO> querySessionTrend(@Param("tenantId") Long tenantId,
                                                  @Param("startDate") LocalDate startDate);

    @Select("""
            SELECT
                DATE_FORMAT(create_time, '%Y-%m-%d') AS date,
                COUNT(1) AS handoffCount
            FROM handoff_request
            WHERE tenant_id = #{tenantId}
              AND create_time >= #{startDate}
            GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d')
            ORDER BY DATE_FORMAT(create_time, '%Y-%m-%d')
            """)
    List<DashboardTrendPointVO> queryHandoffTrend(@Param("tenantId") Long tenantId,
                                                  @Param("startDate") LocalDate startDate);

}
