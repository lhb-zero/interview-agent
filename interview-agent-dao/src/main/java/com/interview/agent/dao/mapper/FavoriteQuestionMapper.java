package com.interview.agent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.agent.model.entity.FavoriteQuestion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 面试题收藏 Mapper
 */
@Mapper
public interface FavoriteQuestionMapper extends BaseMapper<FavoriteQuestion> {
}
