package com.wisdomhub.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.wisdomhub.content.domain.entity.Question;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface QuestionMapper extends BaseMapper<Question> {

    /**
     * 我的问题收藏分页，按收藏时间倒序。
     * 排序键在关系表、可见性在 question 表，只能 join（同 B1.26 的我的收藏）
     */
    IPage<Question> selectFavoritePage(IPage<Question> page,
                                       @Param("userId") Long userId,
                                       @Param("deleted") Integer deleted);
}
