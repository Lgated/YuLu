package com.ityfz.yulu.admin.mapper;

import com.ityfz.yulu.admin.vo.DashboardIntentStatVO;
import com.ityfz.yulu.admin.vo.DashboardTrendPointVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;


import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Select("""
SELECT COUNT(1)
FROM chat_message
WHERE tenant_id = #{tenantId}
  AND sender_type = 'USER'
  AND create_time >= #{startTime}
""")
    Long countUserMessagesSince(@Param("tenantId") Long tenantId, @Param("startTime") LocalDateTime startTime);

    @Select("""
SELECT COUNT(1)
FROM chat_message
WHERE tenant_id = #{tenantId}
  AND sender_type = 'USER'
  AND create_time >= #{startTime}
  AND UPPER(IFNULL(emotion, '')) IN ('ANGRY','NEGATIVE')
""")
    Long countNegativeMessagesSince(@Param("tenantId") Long tenantId, @Param("startTime") LocalDateTime startTime);

    @Select("""
SELECT IFNULL(intent, 'GENERAL') AS intent, COUNT(1) AS count
FROM chat_message
WHERE tenant_id = #{tenantId}
  AND sender_type = 'USER'
  AND create_time >= #{startTime}
GROUP BY IFNULL(intent, 'GENERAL')
ORDER BY count DESC
""")
    List<DashboardIntentStatVO> queryIntentDistribution(@Param("tenantId") Long tenantId, @Param("startTime") LocalDateTime startTime);

}
