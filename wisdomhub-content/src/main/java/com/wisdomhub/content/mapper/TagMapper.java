package com.wisdomhub.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wisdomhub.content.domain.entity.Tag;
import com.wisdomhub.content.domain.vo.HotTagVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TagMapper extends BaseMapper<Tag> {

    /** 热门标签：按已发布未删除文章的关联数倒序。state/deleted 由调用方传 ArticleConstants */
    List<HotTagVO> selectHotTags(@Param("state") Integer state,
                                 @Param("deleted") Integer deleted,
                                 @Param("limit") Integer limit);
}
