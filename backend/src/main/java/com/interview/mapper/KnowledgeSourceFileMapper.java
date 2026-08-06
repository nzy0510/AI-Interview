package com.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.entity.KnowledgeSourceFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface KnowledgeSourceFileMapper extends BaseMapper<KnowledgeSourceFile> {
    @Select("SELECT * FROM knowledge_source_file WHERE id = #{id} FOR UPDATE")
    KnowledgeSourceFile selectByIdForUpdate(@Param("id") Long id);
}
