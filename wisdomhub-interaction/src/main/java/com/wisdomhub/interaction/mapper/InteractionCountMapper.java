package com.wisdomhub.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wisdomhub.interaction.domain.entity.InteractionCount;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface InteractionCountMapper extends BaseMapper<InteractionCount> {

    /**
     * 计数行不存在则插入，存在则原子增减；负值时不会减成负数。
     * 与明细插入/删除在同一个本地事务里。
     */
    @Insert("""
            INSERT INTO interaction_count
                (id, target_type, target_id, like_count, comment_count, favorite_count, create_time, update_time)
            VALUES (#{id}, #{targetType}, #{targetId}, 0, GREATEST(#{delta}, 0), 0, #{now}, #{now})
            ON DUPLICATE KEY UPDATE
                comment_count = GREATEST(comment_count + #{delta}, 0),
                update_time = #{now}
            """)
    int addCommentCount(@Param("id") Long id,
                        @Param("targetType") int targetType,
                        @Param("targetId") Long targetId,
                        @Param("delta") int delta,
                        @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO interaction_count
                (id, target_type, target_id, like_count, comment_count, favorite_count, create_time, update_time)
            VALUES (#{id}, #{targetType}, #{targetId}, GREATEST(#{delta}, 0), 0, 0, #{now}, #{now})
            ON DUPLICATE KEY UPDATE
                like_count = GREATEST(like_count + #{delta}, 0),
                update_time = #{now}
            """)
    int addLikeCount(@Param("id") Long id,
                     @Param("targetType") int targetType,
                     @Param("targetId") Long targetId,
                     @Param("delta") int delta,
                     @Param("now") LocalDateTime now);

    @Select("SELECT like_count FROM interaction_count WHERE target_type = #{targetType} AND target_id = #{targetId}")
    Integer selectLikeCount(@Param("targetType") int targetType, @Param("targetId") Long targetId);

    @Insert("""
            INSERT INTO interaction_count
                (id, target_type, target_id, like_count, comment_count, favorite_count, create_time, update_time)
            VALUES (#{id}, #{targetType}, #{targetId}, 0, 0, GREATEST(#{delta}, 0), #{now}, #{now})
            ON DUPLICATE KEY UPDATE
                favorite_count = GREATEST(favorite_count + #{delta}, 0),
                update_time = #{now}
            """)
    int addFavoriteCount(@Param("id") Long id,
                         @Param("targetType") int targetType,
                         @Param("targetId") Long targetId,
                         @Param("delta") int delta,
                         @Param("now") LocalDateTime now);

    @Select("SELECT favorite_count FROM interaction_count WHERE target_type = #{targetType} AND target_id = #{targetId}")
    Integer selectFavoriteCount(@Param("targetType") int targetType, @Param("targetId") Long targetId);
}
