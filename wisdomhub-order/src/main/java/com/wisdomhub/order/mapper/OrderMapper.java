package com.wisdomhub.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wisdomhub.order.domain.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /** 复用旧单：同用户 + 同商品 + 待支付 + 未过期，取最新一条；并发竞态由支付侧兜底 */
    Order selectReusable(@Param("userId") Long userId,
                         @Param("itemType") Integer itemType,
                         @Param("itemId") Long itemId,
                         @Param("now") LocalDateTime now);
}
