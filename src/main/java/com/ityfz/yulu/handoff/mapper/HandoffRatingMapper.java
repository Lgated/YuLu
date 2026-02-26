package com.ityfz.yulu.handoff.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ityfz.yulu.handoff.entity.HandoffRating;
import com.ityfz.yulu.handoff.vo.HandoffLowScoreVO;
import com.ityfz.yulu.handoff.vo.HandoffRatingTrendPointVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface HandoffRatingMapper extends BaseMapper<HandoffRating> {

    @Select("SELECT COUNT(1) FROM handoff_rating WHERE tenant_id = #{tenantId} AND status IN ('RATED','PROCESSED')")
    Long countRated(@Param("tenantId") Long tenantId);

    @Select("SELECT IFNULL(AVG(score),0) FROM handoff_rating WHERE tenant_id = #{tenantId} AND status IN ('RATED','PROCESSED')")
    Double avgScore(@Param("tenantId") Long tenantId);

    @Select("SELECT COUNT(1) FROM handoff_rating WHERE tenant_id = #{tenantId} AND score >= 4 AND status IN ('RATED','PROCESSED')")
    Long countPositive(@Param("tenantId") Long tenantId);

    @Select("SELECT COUNT(1) FROM handoff_rating WHERE tenant_id = #{tenantId} AND score = 3 AND status IN ('RATED','PROCESSED')")
    Long countNeutral(@Param("tenantId") Long tenantId);

    @Select("SELECT COUNT(1) FROM handoff_rating WHERE tenant_id = #{tenantId} AND score <= 2 AND status IN ('RATED','PROCESSED')")
    Long countNegative(@Param("tenantId") Long tenantId);


    @Select("""
            SELECT
              DATE_FORMAT(submit_time, '%Y-%m-%d') AS date,
              COUNT(1) AS ratedCount,
              ROUND(AVG(score), 2) AS avgScore,
              ROUND(SUM(CASE WHEN score >= 4 THEN 1 ELSE 0 END) * 100.0 / COUNT(1), 2) AS positiveRate
            FROM handoff_rating
            WHERE tenant_id = #{tenantId}
              AND status IN ('RATED', 'PROCESSED')
              AND submit_time IS NOT NULL
              AND DATE(submit_time) >= #{startDate}
            GROUP BY DATE_FORMAT(submit_time, '%Y-%m-%d')
            ORDER BY DATE_FORMAT(submit_time, '%Y-%m-%d')
            """)
    List<HandoffRatingTrendPointVO> queryRatingTrend(@Param("tenantId") Long tenantId,
                                                     @Param("startDate") LocalDate startDate);

    @Select("""
            SELECT
              id,
              handoff_request_id AS handoffRequestId,
              session_id AS sessionId,
              user_id AS userId,
              agent_id AS agentId,
              score,
              comment,
              status,
              submit_time AS submitTime,
              processed_time AS processedTime
            FROM handoff_rating
            WHERE tenant_id = #{tenantId}
              AND status IN ('RATED', 'PROCESSED')
              AND score <= #{maxScore}
              AND submit_time >= #{startTime}
            ORDER BY score ASC, submit_time DESC
            LIMIT #{limit}
            """)
    List<HandoffLowScoreVO> queryLowScoreTop(@Param("tenantId") Long tenantId,
                                             @Param("startTime") LocalDateTime startTime,
                                             @Param("maxScore") Integer maxScore,
                                             @Param("limit") Integer limit);
}
