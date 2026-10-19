package com.wisdomhub.order.api.content;

import com.wisdomhub.order.api.content.dto.ColumnPurchaseDTO;
import com.wisdomhub.order.api.content.dto.ColumnTradeInfoDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/** content-service 的专栏交易信息；order-service 不直连 content 的数据库 */
@FeignClient(name = "content-service", contextId = "orderContent", path = "/internal/content")
public interface ContentFeign {

    @GetMapping("/column/{columnId}/trade")
    ColumnTradeInfoDTO getTradeInfo(@PathVariable("columnId") Long columnId);

    @GetMapping("/column/{columnId}/purchased")
    Boolean hasPurchased(@PathVariable("columnId") Long columnId, @RequestParam("userId") Long userId);

    @PostMapping("/column/{columnId}/purchase")
    void purchase(@PathVariable("columnId") Long columnId, @RequestBody ColumnPurchaseDTO dto);

    @DeleteMapping("/column/{columnId}/purchase")
    void refundPurchase(@PathVariable("columnId") Long columnId,
                        @RequestParam("userId") Long userId,
                        @RequestParam("orderId") Long orderId);
}
