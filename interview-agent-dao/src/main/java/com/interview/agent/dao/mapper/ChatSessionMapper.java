package com.interview.agent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.agent.model.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话会话 Mapper
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
