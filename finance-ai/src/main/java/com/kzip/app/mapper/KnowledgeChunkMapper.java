package com.kzip.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kzip.app.entity.KnowledgeChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {

    @Update("UPDATE knowledge_chunk SET status = #{status} WHERE id = #{id}")
    int updateStatus(Long id, int status);

    @Update("UPDATE knowledge_chunk SET status = 0 WHERE doc_id = #{docId} AND is_deleted = 0")
    int disableByDocId(Long docId);
}