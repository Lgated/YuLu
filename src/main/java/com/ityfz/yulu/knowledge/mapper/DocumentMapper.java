package com.ityfz.yulu.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ityfz.yulu.knowledge.entity.Document;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.data.repository.query.Param;

/**
 * 文档 Mapper
 */
@Mapper
public interface DocumentMapper extends BaseMapper<Document> {


    /**
     * 根据租户id和文档id查找文档
     * @param tenantId
     * @param documentId
     * @return
     */
    Document selectByTenantIdAndId(@Param("tenantId")Long tenantId, @Param("id")Long documentId);

}
