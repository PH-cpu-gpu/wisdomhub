package com.wisdomhub.content.service;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.content.domain.dto.ColumnCreateDTO;
import com.wisdomhub.content.domain.dto.ColumnPurchaseDTO;
import com.wisdomhub.content.domain.vo.ArticleListItemVO;
import com.wisdomhub.content.domain.vo.ColumnTradeInfoVO;
import com.wisdomhub.content.domain.vo.ColumnVO;
import com.wisdomhub.content.domain.vo.PurchasedColumnVO;

public interface ColumnService {

    /** 创建专栏。创建类不幂等（17.3），重复提交真的会建两条。返回专栏 ID */
    Long createColumn(ColumnCreateDTO dto);

    /** 我的专栏分页，按创建时间倒序 */
    PageResponse<ColumnVO> listMyColumns(Integer page, Integer pageSize);

    /** 专栏详情。仅 state=1 且未删除可见，游客可读 */
    ColumnVO getColumnDetail(Long columnId);

    /**
     * 专栏内文章分页，按归入时间升序。只返回 state=2 且未删除的文章，
     * 所以 article_count 可能比 total 大（下架/删除的文章仍占着关系行）
     */
    PageResponse<ArticleListItemVO> listColumnArticles(Long columnId, Integer page, Integer pageSize);

    /**
     * 归入文章。设置状态类操作（17.3），幂等：重复归入返回 200。
     * 要求「专栏是自己的」且「文章是自己已发布的」，返回专栏最新收录数
     */
    int addArticle(Long columnId, Long articleId);

    /** 移出文章。幂等：没归入过也返回 200。只校验专栏属主，不校验文章状态（下架/删除的文章也要能清出去） */
    int removeArticle(Long columnId, Long articleId);

    /** 删除专栏：仅创建者本人。软删专栏行，并物理删掉它的归入关系行（文章本身不受影响） */
    void deleteColumn(Long columnId);

    /** 下单用快照。仅 state=1 且未删除的专栏可见，B2.23 起供 order-service 内部调用 */
    ColumnTradeInfoVO getTradeInfo(Long columnId);

    /** 用户是否已购买该专栏。下单与支付前拦截重复购买 */
    boolean hasPurchased(Long columnId, Long userId);

    /** 支付成功后写入购买记录。同 orderId 重试幂等，已购（不同订单）返回 409 */
    void purchase(Long columnId, ColumnPurchaseDTO dto);

    /** 退款成功后删除购买记录；按 orderId 精确删除，同订单幂等 */
    void refundPurchase(Long columnId, Long userId, Long orderId);

    /** 我的已购专栏分页，按购买时间倒序 */
    PageResponse<PurchasedColumnVO> listPurchasedColumns(Integer page, Integer pageSize);
}
