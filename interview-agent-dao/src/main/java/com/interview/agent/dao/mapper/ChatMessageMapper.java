package com.interview.agent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.agent.model.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话消息 Mapper
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
