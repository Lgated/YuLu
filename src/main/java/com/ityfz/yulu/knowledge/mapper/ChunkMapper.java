package com.ityfz.yulu.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ityfz.yulu.knowledge.entity.Chunk;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 文档切分 Mapper
 */
@Mapper
public interface ChunkMapper extends BaseMapper<Chunk> {


    /**
     * 根据文档ID查询所有 Chunk（按序号排序）
     */
    List<Chunk> selectByDocumentId(@Param("documentId") Long documentId);


    /**
     * 根据文档ID删除所有 Chunk
     */
    void deleteByDocumentId(@Param("documentId") Long documentId);

}
