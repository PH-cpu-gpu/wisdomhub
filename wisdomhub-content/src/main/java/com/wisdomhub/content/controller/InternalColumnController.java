package com.wisdomhub.content.controller;

import com.wisdomhub.common.core.result.R;
import com.wisdomhub.content.domain.dto.ColumnPurchaseDTO;
import com.wisdomhub.content.domain.vo.ColumnTradeInfoVO;
import com.wisdomhub.content.service.ColumnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "服务间专栏controller")
@Validated
@RestController
@RequestMapping("/internal/content/column")
@RequiredArgsConstructor
public class InternalColumnController {

    private final ColumnService columnService;

    @Operation(summary = "下单用专栏快照")
    @GetMapping("/{columnId}/trade")
    public R<ColumnTradeInfoVO> trade(@PathVariable Long columnId) {
        return R.success(columnService.getTradeInfo(columnId));
    }

    @Operation(summary = "用户是否已购买专栏")
    @GetMapping("/{columnId}/purchased")
    public R<Boolean> purchased(@PathVariable Long columnId, @RequestParam Long userId) {
        return R.success(columnService.hasPurchased(columnId, userId));
    }

    @Operation(summary = "支付成功后写入购买记录（Seata 分支）")
    @PostMapping("/{columnId}/purchase")
    public R<Void> purchase(@PathVariable Long columnId, @Valid @RequestBody ColumnPurchaseDTO dto) {
        columnService.purchase(columnId, dto);
        return R.success();
    }
}
