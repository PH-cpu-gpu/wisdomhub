package com.wisdomhub.order.domain.dto;

import lombok.Data;

@Data
public class RefundDTO {

    /** 模拟渠道结果：success / failure / processing，缺省 success */
    private String simulate;
}
