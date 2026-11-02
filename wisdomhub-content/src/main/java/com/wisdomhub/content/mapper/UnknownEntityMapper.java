package com.wisdomhub.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wisdomhub.content.domain.entity.UnknownEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UnknownEntityMapper extends BaseMapper<UnknownEntity> {

    /**
     * 汇总累加。调用方必须先在 unknown_entity_hit 抢到唯一键，只有明细真正新增时才走这里，
     * 否则重复投递/重复抽取会把计数刷高。query_count 列保留但不再写入。
     */
    @Insert("INSERT INTO unknown_entity (id, name, name_norm, extract_count, sample_article_id, "
            + "first_seen, last_seen, create_time, update_time) "
            + "VALUES (#{id}, #{name}, #{nameNorm}, 1, "
            + "#{articleId,jdbcType=BIGINT}, #{now}, #{now}, #{now}, #{now}) "
            + "ON DUPLICATE KEY UPDATE name = #{name}, "
            + "extract_count = extract_count + 1, "
            + "sample_article_id = #{articleId,jdbcType=BIGINT}, "
            + "last_seen = #{now}, update_time = #{now}")
    int upsert(@Param("id") Long id,
               @Param("name") String name,
               @Param("nameNorm") String nameNorm,
               @Param("articleId") Long articleId,
               @Param("now") LocalDateTime now);

    /**
     * 可升级候选：不同文章命中数达标、还没有同名标签/别名，
     * 并且「从未提过」或「上次提案已过期」；仍在待审、被拒绝的都不会重复提。
     */
    @Select("SELECT u.* FROM unknown_entity u "
            + "LEFT JOIN tag t ON t.name_norm = u.name_norm "
            + "LEFT JOIN tag_alias a ON a.alias_norm = u.name_norm "
            + "WHERE u.extract_count >= #{minArticles} "
            + "AND t.id IS NULL AND a.id IS NULL "
            + "AND (u.promoted_proposal_id IS NULL "
            + "OR EXISTS (SELECT 1 FROM tag_proposal p "
            + "WHERE p.id = u.promoted_proposal_id AND p.status = #{expiredStatus})) "
            + "ORDER BY u.extract_count DESC, u.last_seen ASC LIMIT #{limit}")
    List<UnknownEntity> selectPromotable(@Param("minArticles") int minArticles,
                                         @Param("expiredStatus") int expiredStatus,
                                         @Param("limit") int limit);
}
