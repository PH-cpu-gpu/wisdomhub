package com.wisdomhub.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.security.context.UserContext;
import com.wisdomhub.common.web.util.CurrentUserUtil;
import com.wisdomhub.common.web.util.OwnerUtil;
import com.wisdomhub.common.web.util.PageUtil;
import com.wisdomhub.content.constants.ArticleConstants;
import com.wisdomhub.content.constants.ColumnConstants;
import com.wisdomhub.content.domain.dto.ColumnCreateDTO;
import com.wisdomhub.content.domain.dto.ColumnPurchaseDTO;
import com.wisdomhub.content.domain.entity.Article;
import com.wisdomhub.content.domain.entity.Column;
import com.wisdomhub.content.domain.entity.ColumnArticle;
import com.wisdomhub.content.domain.entity.ColumnPurchase;
import com.wisdomhub.content.domain.vo.ArticleListItemVO;
import com.wisdomhub.content.domain.vo.ColumnTradeInfoVO;
import com.wisdomhub.content.domain.vo.ColumnVO;
import com.wisdomhub.content.domain.vo.PurchasedColumnVO;
import com.wisdomhub.content.mapper.ArticleMapper;
import com.wisdomhub.content.mapper.ColumnArticleMapper;
import com.wisdomhub.content.mapper.ColumnMapper;
import com.wisdomhub.content.mapper.ColumnPurchaseMapper;
import com.wisdomhub.content.service.ColumnService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ColumnServiceImpl implements ColumnService {

    private static final String ARTICLE_COUNT_COLUMN = "article_count";

    private final ColumnMapper columnMapper;
    private final ColumnArticleMapper columnArticleMapper;
    private final ColumnPurchaseMapper columnPurchaseMapper;
    private final ArticleMapper articleMapper;

    // ==================== 读 ====================

    @Override
    public PageResponse<ColumnVO> listMyColumns(Integer page, Integer pageSize) {
        Page<Column> result = columnMapper.selectPage(PageUtil.of(page, pageSize),
                new LambdaQueryWrapper<Column>()
                        .eq(Column::getUserId, CurrentUserUtil.id())
                        .eq(Column::getDeleted, ColumnConstants.NOT_DELETED)
                        // 同一秒建的专栏会撞 create_time，用 id 兜底否则翻页会漂移
                        .orderByDesc(Column::getCreateTime)
                        .orderByDesc(Column::getId));
        return PageUtil.toResponse(result, result.getRecords().stream().map(this::toVO).toList());
    }

    @Override
    public ColumnVO getColumnDetail(Long columnId) {
        ColumnVO vo = toVO(requireVisibleColumn(columnId));
        Long userId = UserContext.getUserId();
        vo.setPurchased(userId != null && hasPurchased(columnId, userId));
        return vo;
    }

    @Override
    public ColumnTradeInfoVO getTradeInfo(Long columnId) {
        Column column = requireVisibleColumn(columnId);
        return ColumnTradeInfoVO.builder()
                .columnId(column.getId())
                .authorId(column.getUserId())
                .title(column.getTitle())
                .price(column.getPrice())
                .build();
    }

    @Override
    public PageResponse<ArticleListItemVO> listColumnArticles(Long columnId, Integer page, Integer pageSize) {
        requireVisibleColumn(columnId);
        IPage<Article> result = articleMapper.selectColumnArticlePage(PageUtil.of(page, pageSize),
                columnId, ArticleConstants.PUBLISH, ArticleConstants.NOT_DELETED);
        List<ArticleListItemVO> items = result.getRecords().stream().map(this::toListItem).toList();
        return PageUtil.toResponse(result, items);
    }

    @Override
    public PageResponse<PurchasedColumnVO> listPurchasedColumns(Integer page, Integer pageSize) {
        Page<ColumnPurchase> result = columnPurchaseMapper.selectPage(PageUtil.of(page, pageSize),
                new LambdaQueryWrapper<ColumnPurchase>()
                        .eq(ColumnPurchase::getUserId, CurrentUserUtil.id())
                        .orderByDesc(ColumnPurchase::getCreateTime)
                        .orderByDesc(ColumnPurchase::getId));
        List<ColumnPurchase> purchases = result.getRecords();
        if (purchases.isEmpty()) {
            return PageUtil.toResponse(result, List.of());
        }

        Map<Long, Column> columns = columnMapper.selectList(new LambdaQueryWrapper<Column>()
                        .in(Column::getId, purchases.stream().map(ColumnPurchase::getColumnId).toList()))
                .stream()
                .collect(Collectors.toMap(Column::getId, column -> column));
        List<PurchasedColumnVO> items = purchases.stream()
                .filter(purchase -> {
                    Column column = columns.get(purchase.getColumnId());
                    return column != null
                            && column.getDeleted() == ColumnConstants.NOT_DELETED.byteValue();
                })
                .map(purchase -> {
                    Column column = columns.get(purchase.getColumnId());
                    return PurchasedColumnVO.builder()
                            .columnId(column.getId())
                            .userId(column.getUserId())
                            .title(column.getTitle())
                            .description(column.getDescription())
                            .cover(column.getCover())
                            .articleCount(column.getArticleCount())
                            .state(column.getState() == null ? null : column.getState().intValue())
                            .price(purchase.getPrice())
                            .orderId(purchase.getOrderId())
                            .purchaseTime(purchase.getCreateTime())
                            .build();
                })
                .toList();
        return PageUtil.toResponse(result, items);
    }

    // ==================== 写 ====================

    @Override
    public Long createColumn(ColumnCreateDTO dto) {
        Column column = Column.builder()
                .userId(CurrentUserUtil.id())
                .title(dto.getTitle())
                // DDL 是 NOT NULL DEFAULT ''，显式给空串，不依赖 MP 的 null 字段跳过策略
                .description(dto.getDescription() == null ? "" : dto.getDescription())
                .cover(dto.getCover())
                .price(dto.getPrice() == null ? 0 : dto.getPrice())
                .articleCount(0)
                .state(ColumnConstants.NORMAL.byteValue())
                .deleted(ColumnConstants.NOT_DELETED.byteValue())
                .build();
        columnMapper.insert(column);
        return column.getId();
    }

    @Override
    @Transactional
    public int addArticle(Long columnId, Long articleId) {
        Column column = requireVisibleColumn(columnId);
        OwnerUtil.assertOwner(column.getUserId());
        Article article = requirePublishedArticle(articleId);
        OwnerUtil.assertOwner(article.getAuthorId());

        try {
            columnArticleMapper.insert(ColumnArticle.builder()
                    .columnId(columnId)
                    .articleId(articleId)
                    .build());
        } catch (DuplicateKeyException e) {
            // 已经归入过：设置状态类操作，幂等返回 200
            return column.getArticleCount();
        }
        if (bumpArticleCount(columnId, 1) == 0) {
            // 校验之后专栏被并发下架/删除：抛出去回滚这次插入
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "专栏不存在");
        }
        return readArticleCount(columnId);
    }

    @Override
    @Transactional
    public int removeArticle(Long columnId, Long articleId) {
        Column column = requireVisibleColumnForUpdate(columnId);
        OwnerUtil.assertOwner(column.getUserId());
        boolean joined = columnArticleMapper.exists(new LambdaQueryWrapper<ColumnArticle>()
                .eq(ColumnArticle::getColumnId, columnId)
                .eq(ColumnArticle::getArticleId, articleId));
        if (!joined) {
            // 本来就没归入过：幂等，不是错误
            return column.getArticleCount();
        }
        if (hasPurchase(columnId)) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "专栏已有购买记录，不能移出文章");
        }
        int deleted = columnArticleMapper.delete(new LambdaQueryWrapper<ColumnArticle>()
                .eq(ColumnArticle::getColumnId, columnId)
                .eq(ColumnArticle::getArticleId, articleId));
        if (deleted == 0) {
            // 并发移出：本次不要再减计数
            return column.getArticleCount();
        }
        bumpArticleCount(columnId, -1);
        return readArticleCount(columnId);
    }

    @Override
    @Transactional
    public void deleteColumn(Long columnId) {
        Column column = requireVisibleColumnForUpdate(columnId);
        OwnerUtil.assertOwner(column.getUserId());
        if (hasPurchase(columnId)) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "专栏已有购买记录，不能删除");
        }

        int affected = columnMapper.update(null, new LambdaUpdateWrapper<Column>()
                .set(Column::getDeleted, ColumnConstants.DELETED.byteValue())
                // update(null, wrapper) 不触发自动填充，逻辑删除要自己补 update_time
                .set(Column::getUpdateTime, LocalDateTime.now(ZoneOffset.UTC))
                .eq(Column::getId, columnId)
                .eq(Column::getDeleted, ColumnConstants.NOT_DELETED));
        if (affected == 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "专栏状态已变化，删除未生效");
        }
        // 关系行没有软删语义，留着就是永久孤儿 → 物理删（同 B1.32 删文章的口径）。
        // 有购买记录的专栏已在上面拦截；column_purchase 只在退款时物理删。
        columnArticleMapper.delete(new LambdaQueryWrapper<ColumnArticle>()
                .eq(ColumnArticle::getColumnId, columnId));
    }

    // ==================== 共用 ====================

    private Column requireVisibleColumn(Long columnId) {
        Column column = columnMapper.selectById(columnId);
        if (column == null
                || column.getDeleted() == ColumnConstants.DELETED.byteValue()
                || column.getState() != ColumnConstants.NORMAL.byteValue()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "专栏不存在");
        }
        return column;
    }

    /** 写路径取专栏行并加锁：与购买写 column_purchase 串行化 */
    private Column requireVisibleColumnForUpdate(Long columnId) {
        Column column = columnMapper.selectOne(new LambdaQueryWrapper<Column>()
                .eq(Column::getId, columnId)
                .last("FOR UPDATE"));
        if (column == null
                || column.getDeleted() == ColumnConstants.DELETED.byteValue()
                || column.getState() != ColumnConstants.NORMAL.byteValue()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "专栏不存在");
        }
        return column;
    }

    private boolean hasPurchase(Long columnId) {
        return columnPurchaseMapper.exists(new LambdaQueryWrapper<ColumnPurchase>()
                .eq(ColumnPurchase::getColumnId, columnId));
    }

    @Override
    public boolean hasPurchased(Long columnId, Long userId) {
        return columnPurchaseMapper.exists(new LambdaQueryWrapper<ColumnPurchase>()
                .eq(ColumnPurchase::getColumnId, columnId)
                .eq(ColumnPurchase::getUserId, userId));
    }

    @Override
    @Transactional
    public void purchase(Long columnId, ColumnPurchaseDTO dto) {
        Long userId = CurrentUserUtil.id();
        // 与移出文章 / 删除专栏 / 删除文章用同一把专栏行锁，售出后不允许再改内容
        Column column = requireVisibleColumnForUpdate(columnId);
        if (userId.equals(column.getUserId())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "不能购买自己的专栏");
        }

        ColumnPurchase existing = findPurchase(userId, columnId);
        if (existing != null) {
            if (dto.getOrderId().equals(existing.getOrderId())) {
                return;
            }
            throw new BusinessException(CommonErrorCode.CONFLICT, "已购买该专栏");
        }
        try {
            columnPurchaseMapper.insert(ColumnPurchase.builder()
                    .userId(userId)
                    .columnId(columnId)
                    .orderId(dto.getOrderId())
                    .price(dto.getPrice())
                    .build());
        } catch (DuplicateKeyException e) {
            // 并发写入：同订单视为 Seata 重试，其他订单才是重复购买
            ColumnPurchase latest = findPurchase(userId, columnId);
            if (latest != null && dto.getOrderId().equals(latest.getOrderId())) {
                return;
            }
            throw new BusinessException(CommonErrorCode.CONFLICT, "已购买该专栏");
        }
    }

    private ColumnPurchase findPurchase(Long userId, Long columnId) {
        return columnPurchaseMapper.selectOne(new LambdaQueryWrapper<ColumnPurchase>()
                .eq(ColumnPurchase::getUserId, userId)
                .eq(ColumnPurchase::getColumnId, columnId));
    }

    @Override
    @Transactional
    public void refundPurchase(Long columnId, Long userId, Long orderId) {
        // 锁专栏行，与移出/删除路径串行化；下架或软删的专栏也要能退
        columnMapper.selectOne(new LambdaQueryWrapper<Column>()
                .eq(Column::getId, columnId)
                .last("FOR UPDATE"));
        // 必须带 orderId：退款后重购的新记录属于另一个订单，不能被旧回调删掉
        columnPurchaseMapper.delete(new LambdaQueryWrapper<ColumnPurchase>()
                .eq(ColumnPurchase::getUserId, userId)
                .eq(ColumnPurchase::getColumnId, columnId)
                .eq(ColumnPurchase::getOrderId, orderId));
    }

    /** 只有已发布且未删除的文章能归入专栏，同 B1.26 的 requireVisibleArticle */
    private Article requirePublishedArticle(Long articleId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null
                || article.getDeleted() == ArticleConstants.DELETED.byteValue()
                || article.getState() != ArticleConstants.PUBLISH.byteValue()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "文章不存在");
        }
        return article;
    }

    /**
     * 收录数原子增减，绝不读改写。不 bump update_time（同 B1.26：归入/移出不该把整行写一遍）。
     * delta 为负时带非负安全网：历史漂移时不要把计数减成负数
     */
    private int bumpArticleCount(Long columnId, int delta) {
        LambdaUpdateWrapper<Column> wrapper = new LambdaUpdateWrapper<Column>()
                .setSql(ARTICLE_COUNT_COLUMN + " = " + ARTICLE_COUNT_COLUMN + " + " + delta)
                .eq(Column::getId, columnId)
                .eq(Column::getDeleted, ColumnConstants.NOT_DELETED)
                .eq(Column::getState, ColumnConstants.NORMAL);
        if (delta < 0) {
            wrapper.ge(Column::getArticleCount, 1);
        }
        return columnMapper.update(null, wrapper);
    }

    /** 成功路径读回最新收录数。 */
    private int readArticleCount(Long columnId) {
        Column latest = columnMapper.selectById(columnId);
        if (latest == null || latest.getArticleCount() == null) {
            return 0;
        }
        return latest.getArticleCount();
    }

    private ColumnVO toVO(Column column) {
        return ColumnVO.builder()
                .columnId(column.getId())
                .userId(column.getUserId())
                .title(column.getTitle())
                .description(column.getDescription())
                .cover(column.getCover())
                .price(column.getPrice())
                .articleCount(column.getArticleCount())
                .createTime(column.getCreateTime())
                .build();
    }

    /** 与 ArticleServiceImpl.toListItem 同形。第三次出现再抽公共转换器 */
    private ArticleListItemVO toListItem(Article article) {
        return ArticleListItemVO.builder()
                .articleId(article.getId())
                .authorId(article.getAuthorId())
                .title(article.getTitle())
                .summary(article.getSummary())
                .publishTime(article.getPublishTime())
                .viewCount(article.getViewCount())
                .likeCount(article.getLikeCount())
                .commentCount(article.getCommentCount())
                .favoriteCount(article.getFavoriteCount())
                .build();
    }
}
