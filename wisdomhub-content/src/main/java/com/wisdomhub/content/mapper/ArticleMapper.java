package com.wisdomhub.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wisdomhub.content.domain.entity.Article;
import org.apache.ibatis.annotations.Mapper;

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

}
