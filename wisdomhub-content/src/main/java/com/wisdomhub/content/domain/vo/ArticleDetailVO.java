package com.wisdomhub.content.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** 文章详情。正文给消毒后的 html；markdown 原文只走属主私有的 /source */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleDetailVO {

    private Long articleId;

    /** 只给 ID，昵称头像由前端另调 user-service，详情读路径不跨服务 */
    private Long authorId;

    private String title;

    private String summary;

    /** 渲染并消毒后的正文；locked=true 时为 null */
    private String html;

    /** 是否被付费墙锁住。摘要仍返回，作为试读 */
    private Boolean locked;

    /** 包含本文的专栏及当前用户的购买状态；不在任何专栏时为空列表 */
    private List<ArticleColumnVO> columns;

    private LocalDateTime publishTime;

    private Integer viewCount;

    private Integer likeCount;

    private Integer commentCount;

    private Integer favoriteCount;

    /** 当前登录用户是否已点赞；详情是可选登录路径，游客恒 false（按钮给他点了也是 401） */
    private Boolean liked;

    /** 当前登录用户是否已收藏，同上 */
    private Boolean collected;
}
