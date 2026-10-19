package com.wisdomhub.content.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 已购专栏列表项。price 是实付价快照，不是专栏当前价 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchasedColumnVO {

    private Long columnId;

    private Long userId;

    private String title;

    private String description;

    private String cover;

    private Integer articleCount;

    /** 1-正常 2-下架 */
    private Integer state;

    /** 实付价，单位为分 */
    private Integer price;

    private Long orderId;

    private LocalDateTime purchaseTime;
}
