package com.wisdomhub.content.service;

import com.wisdomhub.content.domain.dto.TagAliasDTO;
import com.wisdomhub.content.domain.vo.TagAliasVO;

import java.util.List;

public interface TagAliasService {

    List<TagAliasVO> list(Long tagId);

    TagAliasVO add(Long tagId, TagAliasDTO dto);

    void delete(Long tagId, Long aliasId);
}
