package com.wisdomhub.recommendation.api.search;

import com.wisdomhub.recommendation.api.search.dto.ArticleRecallDTO;
import com.wisdomhub.recommendation.api.search.dto.HotRequest;
import com.wisdomhub.recommendation.api.search.dto.RecallRequest;
import com.wisdomhub.recommendation.api.search.dto.SimilarRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/** search-service 召回出口；推荐侧不直连 ES */
@FeignClient(name = "search-service", contextId = "searchRecall", path = "/internal/search")
public interface SearchFeign {

    @PostMapping("/recall")
    List<ArticleRecallDTO> recall(@RequestBody RecallRequest request);

    @PostMapping("/similar")
    List<ArticleRecallDTO> similar(@RequestBody SimilarRequest request);

    @PostMapping("/hot")
    List<ArticleRecallDTO> hot(@RequestBody HotRequest request);
}
