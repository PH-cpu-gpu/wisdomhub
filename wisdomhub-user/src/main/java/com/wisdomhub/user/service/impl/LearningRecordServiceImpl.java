package com.wisdomhub.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.web.util.PageUtil;
import com.wisdomhub.user.domain.entity.UserLearningRecord;
import com.wisdomhub.user.domain.vo.LearningRecordVO;
import com.wisdomhub.user.mapper.UserLearningRecordMapper;
import com.wisdomhub.user.service.LearningRecordService;
import com.wisdomhub.user.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl implements LearningRecordService {

    private final UserLearningRecordMapper userLearningRecordMapper;

    @Override
    public void add(String technologyName) {
        Long userId = CurrentUserUtil.id();
        boolean exists = userLearningRecordMapper.exists(new LambdaQueryWrapper<UserLearningRecord>()
                .eq(UserLearningRecord::getUserId, userId)
                .eq(UserLearningRecord::getTechnologyName, technologyName));
        if (exists) {
            // 已经在记录里就什么都不动：重复加入不能把"已掌握"重置回"学习中"
            return;
        }
        try {
            userLearningRecordMapper.insert(UserLearningRecord.builder()
                    .userId(userId)
                    .technologyName(technologyName)
                    .mastered(false)
                    .build());
        } catch (DuplicateKeyException ignored) {
            // 并发下被别的请求抢先插入了，结果一致，当成功
        }
    }

    @Override
    public void remove(String technologyName) {
        userLearningRecordMapper.delete(new LambdaQueryWrapper<UserLearningRecord>()
                .eq(UserLearningRecord::getUserId, CurrentUserUtil.id())
                .eq(UserLearningRecord::getTechnologyName, technologyName));
    }

    @Override
    public void markMastered(String technologyName) {
        setMastered(technologyName, true);
    }

    @Override
    public void unmarkMastered(String technologyName) {
        setMastered(technologyName, false);
    }

    @Override
    public PageResponse<LearningRecordVO> list(Integer page, Integer pageSize) {
        Page<UserLearningRecord> result = userLearningRecordMapper.selectPage(PageUtil.of(page, pageSize),
                new LambdaQueryWrapper<UserLearningRecord>()
                        .eq(UserLearningRecord::getUserId, CurrentUserUtil.id())
                        .orderByDesc(UserLearningRecord::getUpdateTime));
        return PageUtil.toResponse(result, result.getRecords().stream()
                .map(record -> LearningRecordVO.builder()
                        .technologyName(record.getTechnologyName())
                        .mastered(record.getMastered())
                        .updateTime(record.getUpdateTime())
                        .build())
                .toList());
    }

    private void setMastered(String technologyName, boolean mastered) {
        UserLearningRecord patch = new UserLearningRecord();
        patch.setMastered(mastered);
        // update 影响的是"匹配到"的行数（Connector/J 默认 useAffectedRows=false），所以重复标记也算命中
        int updated = userLearningRecordMapper.update(patch, new LambdaUpdateWrapper<UserLearningRecord>()
                .eq(UserLearningRecord::getUserId, CurrentUserUtil.id())
                .eq(UserLearningRecord::getTechnologyName, technologyName));
        if (updated == 0) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "学习记录不存在");
        }
    }
}
