package com.wisdomhub.content.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/** 内容自有计数对账（回答数 / 专栏文章数）的 grouped count 查询。 */
@Mapper
public interface CountReconcileMapper {

    List<Map<String, Object>> countColumnArticlesByColumn();

    List<Map<String, Object>> countAnswersByQuestion();
}
