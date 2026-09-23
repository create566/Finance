package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.KnowledgeDoc;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface KnowledgeDocMapper extends BaseMapper<KnowledgeDoc> {

    @Update("UPDATE knowledge_doc SET status = #{status} WHERE id = #{id}")
    int updateStatus(Long id, int status);

    @Update("UPDATE knowledge_doc SET chunk_count = #{chunkCount}, status = #{status} WHERE id = #{id}")
    int updateChunkCountAndStatus(Long id, int chunkCount, int status);
}