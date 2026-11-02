package com.wisdomhub.knowledge.service;

import com.wisdomhub.knowledge.api.ContentUnknownEntityFeign;
import com.wisdomhub.knowledge.api.dto.UnknownEntityHitDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 把「图里没有的实体」报给 content 做证据留存。
 * 采集是旁路：content 不可用时只记日志，绝不能拖垮图谱写入或查询。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnknownEntityReporter {

    /** 与 content 的 unknown_entity_hit.source 对齐：1=抽取 */
    private static final int SOURCE_EXTRACT = 1;

    /** content 侧单次最多收 100 个，抽出的实体远小于这个量级 */
    private static final int MAX_NAMES = 100;

    private final ContentUnknownEntityFeign contentUnknownEntityFeign;

    /** 抽取丢弃的实体：同一篇文章重复抽取不会重复计数 */
    public void reportExtract(Long articleId, Collection<String> names) {
        if (articleId == null || names == null || names.isEmpty()) {
            return;
        }
        List<String> pending = names.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .distinct()
                .limit(MAX_NAMES)
                .toList();
        if (pending.isEmpty()) {
            return;
        }
        send(UnknownEntityHitDTO.builder()
                .articleId(articleId)
                .names(pending)
                .build());
    }

    private void send(UnknownEntityHitDTO dto) {
        try {
            contentUnknownEntityFeign.recordHits(dto);
        } catch (Exception e) {
            log.warn("未收录实体上报失败，忽略: articleId={} names={}",
                    dto.getArticleId(), dto.getNames(), e);
        }
    }
}
