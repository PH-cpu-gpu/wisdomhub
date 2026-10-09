package com.wisdomhub.content.service;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.content.domain.dto.ArticleDraftDTO;
import com.wisdomhub.content.domain.dto.ArticlePublishDTO;
import com.wisdomhub.content.domain.vo.ArticleBriefVO;
import com.wisdomhub.content.domain.vo.ArticleDetailVO;
import com.wisdomhub.content.domain.vo.ArticleListItemVO;
import com.wisdomhub.content.domain.vo.ArticleSourceVO;
import com.wisdomhub.content.domain.vo.DraftBriefVO;

import java.util.List;

public interface ArticleService {

    Long countArticleByUserId(Long authorId);

    /** 按 ID 批量取文章摘要，只返回已发布且未删除的；返回顺序不保证，由调用方按需重排 */
    List<ArticleBriefVO> listBrief(List<Long> articleIds);

    /** 发布文章：正文写 MongoDB、元数据写 MySQL。返回文章 ID */
    Long publish(ArticlePublishDTO dto);

    /** 新建草稿（首次自动保存），返回文章 ID */
    Long createDraft(ArticleDraftDTO dto);

    /** 更新草稿（后续自动保存）。只接受 state=1，已发布文章会 409 */
    Long updateDraft(Long articleId, ArticleDraftDTO dto);

    /** 删除草稿。只接受 state=1 */
    void deleteDraft(Long articleId);

    /** 草稿箱分页，按最后编辑时间倒序 */
    PageResponse<DraftBriefVO> listDrafts(Integer page, Integer pageSize);

    /** 编辑器源数据：属主本人、任意非删除状态（1/2/3）都返回 markdown 与 state */
    ArticleSourceVO getSource(Long articleId);

    /** 文章详情：跨库组装 MySQL 元数据与 MongoDB 正文。仅 state=2 且未删除可见；登录用户按窗口累加阅读量，游客只读不计数 */
    ArticleDetailVO getDetail(Long articleId);

    /**
     * 公开文章列表：分页 + 排序（new=发布时间倒序 / hot=阅读量快照倒序）+ 可选 tagId / authorId 筛选。
     * 只返回 state=2 且未删除；tagId 走 article_tag 半连接子查询。
     */
    PageResponse<ArticleListItemVO> listArticles(Integer page, Integer pageSize, String sort,
                                                 Long tagId, Long authorId);

    /**
     * 我的收藏分页，按收藏时间倒序（不是文章发布时间）。join 过滤掉草稿 / 下架 / 已删的文章，
     */
    PageResponse<ArticleListItemVO> listMyFavorites(Integer page, Integer pageSize);
}
