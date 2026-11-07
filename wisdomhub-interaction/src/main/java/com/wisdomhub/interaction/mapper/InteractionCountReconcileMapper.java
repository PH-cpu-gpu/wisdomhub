package com.wisdomhub.interaction.mapper;

import com.wisdomhub.interaction.domain.entity.InteractionCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 对账用的明细聚合：按 targetType + targetId 统计真实计数。 */
@Mapper
public interface InteractionCountReconcileMapper {

    @Select("""
            SELECT target_type, target_id,
                   SUM(like_count) AS like_count,
                   SUM(comment_count) AS comment_count,
                   SUM(favorite_count) AS favorite_count
            FROM (
                SELECT target_type, target_id, 1 AS like_count, 0 AS comment_count, 0 AS favorite_count
                FROM content_like WHERE target_type = #{targetType}
                UNION ALL
                SELECT target_type, target_id, 0, 0, 1
                FROM content_favorite WHERE target_type = #{targetType}
                UNION ALL
                SELECT target_type, target_id, 0, 1, 0
                FROM comment WHERE target_type = #{targetType} AND deleted = 0
            ) x
            GROUP BY target_type, target_id
            """)
    List<InteractionCount> countDetails(@Param("targetType") int targetType);
}
