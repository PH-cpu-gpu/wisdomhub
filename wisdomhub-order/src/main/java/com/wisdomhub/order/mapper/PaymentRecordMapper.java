package com.wisdomhub.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wisdomhub.order.domain.entity.PaymentRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {
}
