package com.ityfz.yulu.handoff.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ityfz.yulu.handoff.entity.HandoffRating;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
