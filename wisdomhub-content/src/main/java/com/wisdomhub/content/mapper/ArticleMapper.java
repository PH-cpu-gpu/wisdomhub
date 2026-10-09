package com.wisdomhub.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.wisdomhub.content.domain.entity.Article;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 文章 Mapper 接口
 * </p>
 *
 * @author PulsarHaze
 * @since 2026-10-02
 */
@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    /**
     * 我的收藏分页。
     */
    IPage<Article> selectFavoritePage(IPage<Article> page,
                                      @Param("userId") Long userId,
                                      @Param("state") Integer state,
                                      @Param("deleted") Integer deleted);

    /**
     * 专栏内文章分页，按归入时间升序（章节顺序，不是文章发布时间）。
     */
    IPage<Article> selectColumnArticlePage(IPage<Article> page,
                                           @Param("columnId") Long columnId,
                                           @Param("state") Integer state,
                                           @Param("deleted") Integer deleted);
}
