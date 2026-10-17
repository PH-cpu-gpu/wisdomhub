package com.wisdomhub.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wisdomhub.notification.domain.entity.NotificationSetting;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationSettingMapper extends BaseMapper<NotificationSetting> {
}
