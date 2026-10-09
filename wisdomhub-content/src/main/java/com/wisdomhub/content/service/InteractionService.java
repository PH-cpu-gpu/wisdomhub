package com.wisdomhub.content.service;

/**
 * 文章点赞与收藏。
 */
public interface InteractionService {

    /** 点赞。已点过则什么都不做。返回文章最新的点赞数 */
    int like(Long articleId);

    /** 取消点赞。没点过也返回 200。返回文章最新的点赞数 */
    int unlike(Long articleId);

    /** 收藏。已收藏则什么都不做。返回文章最新的收藏数 */
    int collect(Long articleId);

    /** 取消收藏。没收藏过也返回 200。返回文章最新的收藏数 */
    int uncollect(Long articleId);
}
