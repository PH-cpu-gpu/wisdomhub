package com.wisdomhub.content.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** 计数对账用的 grouped count 查询。 */
@Mapper
public interface CountReconcileMapper {

    List<Map<String, Object>> countArticleLikesByArticle();

    List<Map<String, Object>> countArticleFavoritesByArticle();

    List<Map<String, Object>> countAnswerLikesByAnswer();

    List<Map<String, Object>> countQuestionFavoritesByQuestion();

    List<Map<String, Object>> countColumnArticlesByColumn();

    List<Map<String, Object>> countCommentsByTarget(@Param("targetType") Integer targetType);

    List<Map<String, Object>> countAnswersByQuestion();
}
