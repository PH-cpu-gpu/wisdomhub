package com.wisdomhub.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.content.domain.dto.TagAliasDTO;
import com.wisdomhub.content.domain.entity.Tag;
import com.wisdomhub.content.domain.entity.TagAlias;
import com.wisdomhub.content.domain.vo.TagAliasVO;
import com.wisdomhub.content.mapper.TagAliasMapper;
import com.wisdomhub.content.mapper.TagMapper;
import com.wisdomhub.content.service.TagAliasService;
import com.wisdomhub.content.util.TagNameNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TagAliasServiceImpl implements TagAliasService {

    private final TagMapper tagMapper;
    private final TagAliasMapper tagAliasMapper;

    @Override
    public List<TagAliasVO> list(Long tagId) {
        requireTag(tagId);
        return tagAliasMapper.selectList(new LambdaQueryWrapper<TagAlias>()
                        .eq(TagAlias::getTagId, tagId)
                        .orderByAsc(TagAlias::getAlias))
                .stream()
                .map(alias -> TagAliasVO.builder()
                        .id(alias.getId())
                        .tagId(alias.getTagId())
                        .alias(alias.getAlias())
                        .build())
                .toList();
    }

    @Override
    public TagAliasVO add(Long tagId, TagAliasDTO dto) {
        requireTag(tagId);
        String alias = dto.getAlias().trim();
        String aliasNorm = TagNameNormalizer.normalize(alias);
        if (aliasNorm.isEmpty()) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "别名不能为空");
        }
        if (aliasNorm.length() > TagNameNormalizer.MAX_LENGTH) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "别名归一化后不能超过50字");
        }
        // 别名不能和任何标签名撞，否则同一个归一化名会指向两个标签
        if (tagMapper.exists(new LambdaQueryWrapper<Tag>().eq(Tag::getNameNorm, aliasNorm))) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "别名与已有标签名冲突");
        }
        if (tagAliasMapper.exists(new LambdaQueryWrapper<TagAlias>().eq(TagAlias::getAliasNorm, aliasNorm))) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "别名已存在");
        }
        TagAlias entity = TagAlias.builder()
                .tagId(tagId)
                .alias(alias)
                .aliasNorm(aliasNorm)
                .build();
        tagAliasMapper.insert(entity);
        return TagAliasVO.builder()
                .id(entity.getId())
                .tagId(entity.getTagId())
                .alias(entity.getAlias())
                .build();
    }

    @Override
    public void delete(Long tagId, Long aliasId) {
        requireTag(tagId);
        TagAlias alias = tagAliasMapper.selectById(aliasId);
        if (alias == null || !tagId.equals(alias.getTagId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "别名不存在");
        }
        tagAliasMapper.deleteById(aliasId);
    }

    private void requireTag(Long tagId) {
        if (tagMapper.selectById(tagId) == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "标签不存在");
        }
    }
}
