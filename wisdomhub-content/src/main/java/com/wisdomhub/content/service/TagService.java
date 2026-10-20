package com.wisdomhub.content.service;

import com.wisdomhub.content.domain.dto.TagDTO;
import com.wisdomhub.content.domain.vo.HotTagVO;
import com.wisdomhub.content.domain.vo.TagSyncVO;
import com.wisdomhub.content.domain.vo.TagVO;

import java.time.LocalDateTime;
import java.util.List;

public interface TagService {

    /** 全量标签字典。走两级缓存 */
    List<TagVO> listAllTags();

    /** 对账分页：按 (updateTime, id) 游标，since 为 epoch 表示全量。不走缓存 */
    List<TagSyncVO> syncTags(LocalDateTime since, Long lastId, Integer size);

    /** 热门标签。入参 limit 必须已由 TagConstants.clampHotLimit 钳制过。走两级缓存 */
    List<HotTagVO> listHotTags(Integer limit);

    Long createTag(TagDTO dto);

    void updateTag(Long tagId, TagDTO dto);

    /** 物理删除标签：先删 article_tag 关联，再删 tag 本体 */
    void deleteTag(Long tagId);

    /** 归一化标签 ID：去 null、去重、保序；含不存在的标签抛 400。供发布流程调用 */
    List<Long> normalizeTagIds(List<Long> tagIds);

    /** 文章标签变更后失效热门缓存。必须跨 bean 调用才会过 AOP 代理 */
    void evictHotTags();
}
