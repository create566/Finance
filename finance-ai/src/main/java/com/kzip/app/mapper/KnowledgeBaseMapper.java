package com.kzip.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kzip.app.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {

    /**
     * 原子自增文档数与切片数（并发安全）：并行向量化时多条线程对同一知识库计数做
     * 先查后改会产生丢失更新，必须用 SQL 原子自增。
     */
    @Update("UPDATE knowledge_base SET doc_count = doc_count + 1, chunk_count = chunk_count + #{deltaChunkCount}, update_time = NOW() WHERE id = #{id}")
    int incrementDocAndChunk(@Param("id") Long id, @Param("deltaChunkCount") int deltaChunkCount);

    /**
     * 原子自减文档数与切片数（下限 0）。
     */
    @Update("UPDATE knowledge_base SET doc_count = GREATEST(0, doc_count - 1), chunk_count = GREATEST(0, chunk_count - #{deltaChunkCount}), update_time = NOW() WHERE id = #{id}")
    int decrementDocAndChunk(@Param("id") Long id, @Param("deltaChunkCount") int deltaChunkCount);

    /**
     * 原子自增切片数（文档已存在、仅补切片时使用）。
     */
    @Update("UPDATE knowledge_base SET chunk_count = chunk_count + #{chunkCount}, update_time = NOW() WHERE id = #{id}")
    int incrementChunkOnly(@Param("id") Long id, @Param("chunkCount") int chunkCount);
}