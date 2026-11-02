package com.wisdomhub.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wisdomhub.content.domain.entity.UnknownEntityHit;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface UnknownEntityHitMapper extends BaseMapper<UnknownEntityHit> {

    /** 唯一键 (name_norm, source, ref_key) 挡重复；返回 1 才算新证据，汇总表只在这时累加 */
    @Insert("INSERT IGNORE INTO unknown_entity_hit (id, name_norm, source, ref_key, create_time) "
            + "VALUES (#{id}, #{nameNorm}, #{source}, #{refKey}, #{createTime})")
    int insertIgnore(@Param("id") Long id,
                     @Param("nameNorm") String nameNorm,
                     @Param("source") int source,
                     @Param("refKey") String refKey,
                     @Param("createTime") LocalDateTime createTime);
}
