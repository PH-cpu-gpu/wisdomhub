package com.wisdomhub.content.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.web.util.PageUtil;
import com.wisdomhub.content.constants.UnknownEntityConstants;
import com.wisdomhub.content.domain.dto.UnknownEntityHitDTO;
import com.wisdomhub.content.domain.entity.Tag;
import com.wisdomhub.content.domain.entity.TagAlias;
import com.wisdomhub.content.domain.entity.UnknownEntity;
import com.wisdomhub.content.domain.vo.UnknownEntityVO;
import com.wisdomhub.content.mapper.TagAliasMapper;
import com.wisdomhub.content.mapper.TagMapper;
import com.wisdomhub.content.mapper.UnknownEntityHitMapper;
import com.wisdomhub.content.mapper.UnknownEntityMapper;
import com.wisdomhub.content.service.UnknownEntityService;
import com.wisdomhub.content.util.TagNameNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 未收录实体采集。两张表分工：hit 表用唯一键做去重，汇总表只做累加，
 * 所以重复投递、重复抽取都不会把计数刷高。证据只来自文章抽取，不再收查询 miss。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnknownEntityServiceImpl implements UnknownEntityService {

    private static final int MAX_NAMES = 100;

    private final UnknownEntityMapper unknownEntityMapper;
    private final UnknownEntityHitMapper unknownEntityHitMapper;
    private final TagMapper tagMapper;
    private final TagAliasMapper tagAliasMapper;

    @Override
    @Transactional
    public int recordHits(UnknownEntityHitDTO dto) {
        Map<String, String> candidates = candidates(dto.getNames());
        if (candidates.isEmpty()) {
            return 0;
        }
        Long articleId = dto.getArticleId();
        if (articleId == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "缺少文章 ID");
        }
        String refKey = String.valueOf(articleId);

        Set<String> known = knownNorms(candidates.keySet());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int recorded = 0;
        for (Map.Entry<String, String> entry : candidates.entrySet()) {
            String nameNorm = entry.getKey();
            if (known.contains(nameNorm)) {
                // 上报方解析失败时可能把已有标签也送进来，这里兜一道，免得候选里出现已知技术
                continue;
            }
            if (unknownEntityHitMapper.insertIgnore(IdWorker.getId(), nameNorm,
                    UnknownEntityConstants.SOURCE_EXTRACT, refKey, now) == 0) {
                continue;
            }
            unknownEntityMapper.upsert(IdWorker.getId(), entry.getValue(), nameNorm, articleId, now);
            recorded++;
        }
        if (recorded > 0) {
            log.info("未收录实体证据已记录: articleId={} 新增={}", articleId, recorded);
        }
        return recorded;
    }

    @Override
    public PageResponse<UnknownEntityVO> page(String keyword, Boolean promoted, Integer minExtractCount,
                                              Integer page, Integer pageSize) {
        String kw = StrUtil.trim(keyword);
        Page<UnknownEntity> result = unknownEntityMapper.selectPage(PageUtil.of(page, pageSize),
                new LambdaQueryWrapper<UnknownEntity>()
                        .and(StrUtil.isNotEmpty(kw), wrapper -> wrapper
                                .like(UnknownEntity::getName, kw)
                                .or()
                                .like(UnknownEntity::getNameNorm, kw))
                        .ge(minExtractCount != null, UnknownEntity::getExtractCount, minExtractCount)
                        .isNotNull(Boolean.TRUE.equals(promoted), UnknownEntity::getPromotedProposalId)
                        .isNull(Boolean.FALSE.equals(promoted), UnknownEntity::getPromotedProposalId)
                        .orderByDesc(UnknownEntity::getExtractCount)
                        .orderByDesc(UnknownEntity::getLastSeen)
                        .orderByDesc(UnknownEntity::getId));
        return PageUtil.toResponse(result, result.getRecords().stream().map(this::toVO).toList());
    }

    /** 归一化后去重；按归一化名排序，并发批次的行锁顺序一致，减少死锁 */
    private Map<String, String> candidates(List<String> names) {
        Map<String, String> result = new TreeMap<>();
        if (names == null) {
            return result;
        }
        for (String raw : names.subList(0, Math.min(names.size(), MAX_NAMES))) {
            String name = StrUtil.trim(raw);
            if (StrUtil.isEmpty(name) || name.length() > TagNameNormalizer.MAX_LENGTH) {
                continue;
            }
            String nameNorm = TagNameNormalizer.normalize(name);
            if (nameNorm.isEmpty() || nameNorm.length() > TagNameNormalizer.MAX_LENGTH) {
                continue;
            }
            // 后出现的写法覆盖前面的，保留最近一次原文
            result.put(nameNorm, name);
        }
        return result;
    }

    /** 已经是标签名或别名的，不再收进未收录候选 */
    private Set<String> knownNorms(Collection<String> norms) {
        Set<String> known = new HashSet<>();
        if (norms.isEmpty()) {
            return known;
        }
        tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                        .select(Tag::getNameNorm)
                        .in(Tag::getNameNorm, norms))
                .forEach(tag -> known.add(tag.getNameNorm()));
        tagAliasMapper.selectList(new LambdaQueryWrapper<TagAlias>()
                        .select(TagAlias::getAliasNorm)
                        .in(TagAlias::getAliasNorm, norms))
                .forEach(alias -> known.add(alias.getAliasNorm()));
        return known;
    }

    private UnknownEntityVO toVO(UnknownEntity entity) {
        return UnknownEntityVO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .nameNorm(entity.getNameNorm())
                .extractCount(entity.getExtractCount())
                .queryCount(entity.getQueryCount())
                .sampleArticleId(entity.getSampleArticleId())
                .promotedProposalId(entity.getPromotedProposalId())
                .firstSeen(entity.getFirstSeen())
                .lastSeen(entity.getLastSeen())
                .build();
    }

}
