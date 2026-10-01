package com.wisdomhub.common.web.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 时间字段的 UTC 自动填充。
 *
 * <p>全项目时间按 UTC 存储，而每个实体都有 createTime / updateTime，所以填充逻辑集中在这一处，
 *
 * <p>实体字段需要标 {@code @TableField(fill = FieldFill.INSERT)}（或 INSERT_UPDATE），
 * 否则 strictXxxFill 按 MyBatis-Plus 的约定不会生效。
 *
 * <p>激活条件放在类上：MyBatis-Plus 是本模块的 optional 依赖，
 * 只有真正用 MP 的服务会加载本类；条件标在 @Bean 方法上会因返回类型缺失产生类加载风险。
 */
@AutoConfiguration
@ConditionalOnClass(MetaObjectHandler.class)
public class MybatisTimeConfiguration {

    @Bean
    public MetaObjectHandler wisdomhubMetaObjectHandler() {
        return new UtcMetaObjectHandler();
    }

    static final class UtcMetaObjectHandler implements MetaObjectHandler {

        private static final String CREATE_TIME = "createTime";
        private static final String UPDATE_TIME = "updateTime";

        @Override
        public void insertFill(MetaObject metaObject) {
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            strictInsertFill(metaObject, CREATE_TIME, LocalDateTime.class, now);
            strictInsertFill(metaObject, UPDATE_TIME, LocalDateTime.class, now);
        }

        @Override
        public void updateFill(MetaObject metaObject) {
            strictUpdateFill(metaObject, UPDATE_TIME, LocalDateTime.class, LocalDateTime.now(ZoneOffset.UTC));
        }
    }
}
