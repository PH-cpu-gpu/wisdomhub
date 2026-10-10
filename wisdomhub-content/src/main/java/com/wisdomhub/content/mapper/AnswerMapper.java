package com.wisdomhub.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.wisdomhub.content.domain.entity.Answer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AnswerMapper extends BaseMapper<Answer> {

    /**
     * 回答分页：采纳的那条置顶，其余按发表时间升序。
     * 置顶用 null 安全的 {@code <=>}，未采纳（参数为 null）时全为 0，等价于纯时间序
     */
    IPage<Answer> selectAnswerPage(IPage<Answer> page,
                                   @Param("questionId") Long questionId,
                                   @Param("acceptedAnswerId") Long acceptedAnswerId,
                                   @Param("deleted") Integer deleted);
}
