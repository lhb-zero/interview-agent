package com.interview.agent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.agent.model.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库文档 Mapper
 */
@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {
}
