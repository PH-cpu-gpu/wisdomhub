package com.wisdomhub.search.service;

import com.wisdomhub.search.domain.dto.HotDTO;
import com.wisdomhub.search.domain.dto.RecallDTO;
import com.wisdomhub.search.domain.dto.SimilarDTO;
import com.wisdomhub.search.domain.vo.ArticleRecallVO;
import com.wisdomhub.search.domain.vo.VideoRecallVO;

import java.util.List;

/**
 * 召回出口，只回候选集，不分页、不高亮。
 */
public interface RecallService {

    List<ArticleRecallVO> recall(RecallDTO dto);

    List<ArticleRecallVO> similar(SimilarDTO dto);

    List<ArticleRecallVO> hot(HotDTO dto);

    List<VideoRecallVO> videoRecall(RecallDTO dto);

    List<VideoRecallVO> videoHot(HotDTO dto);
}
