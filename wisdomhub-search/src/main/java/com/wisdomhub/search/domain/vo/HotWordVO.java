package com.wisdomhub.search.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 热搜榜的一项*/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotWordVO {

    private String keyword;

    private Integer count;
}
