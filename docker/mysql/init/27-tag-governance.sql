-- =============================================================================
-- 标签治理：归一化、别名、关系真值、提案与未知实体采集
-- 与 23-content.sql 同库；已初始化过的卷需手工执行本文件，init 目录只在空卷时自动跑。
-- 表继承库排序规则 utf8mb4_0900_as_cs；归一化列由应用层小写后写入，唯一约束才有效。
-- =============================================================================

USE `wisdomhub_content`;

SET NAMES utf8mb4;

-- 标签名归一化列。先加可空列回填，再加 NOT NULL + 唯一键，避免全表默认空串撞唯一索引
ALTER TABLE tag
    ADD COLUMN name_norm VARCHAR(50) NULL COMMENT '归一化标签名，应用层维护' AFTER name;

UPDATE tag
SET name_norm = LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(name, ' ', ''), '-', ''), '_', ''), '.', ''), '·', ''));

ALTER TABLE tag
    MODIFY COLUMN name_norm VARCHAR(50) NOT NULL COMMENT '归一化标签名，应用层维护',
    ADD UNIQUE KEY uk_name_norm (name_norm);

-- 别名：alias_norm 全局唯一，且不得与任何 tag.name_norm 相同（跨表约束由应用层保证）
CREATE TABLE tag_alias (
    id BIGINT PRIMARY KEY COMMENT '雪花 ID',
    tag_id BIGINT NOT NULL COMMENT '所属标签 ID',
    alias VARCHAR(50) NOT NULL COMMENT '别名原文',
    alias_norm VARCHAR(50) NOT NULL COMMENT '归一化别名，应用层维护',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    UNIQUE KEY uk_alias_norm (alias_norm),
    INDEX idx_tag_id (tag_id)
) ENGINE=InnoDB COMMENT='标签别名';

-- 关系真值。Neo4j 的 Tag→Tag 边由这张表投影，seed 之后不再以 JSON 为准
CREATE TABLE tag_relation (
    id BIGINT PRIMARY KEY COMMENT '雪花 ID',
    from_tag_id BIGINT NOT NULL COMMENT '起点标签 ID',
    to_tag_id BIGINT NOT NULL COMMENT '终点标签 ID',
    relation_type VARCHAR(20) NOT NULL COMMENT 'BASED_ON/USES/PREREQUISITE/SIMILAR_TO/BELONGS_TO',
    source TINYINT NOT NULL DEFAULT 2 COMMENT '1=种子 2=管理员 3=AI 审批',
    proposal_id BIGINT NULL COMMENT '来源提案 ID',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    UNIQUE KEY uk_relation (from_tag_id, to_tag_id, relation_type),
    INDEX idx_to (to_tag_id, relation_type)
) ENGINE=InnoDB COMMENT='标签关系真值';

-- 提案。pending_key 是生成列：只有 status=0 时取值，唯一索引因此只约束「同一目标只能有一个待审提案」
CREATE TABLE tag_proposal (
    id BIGINT PRIMARY KEY COMMENT '雪花 ID',
    proposal_type TINYINT NOT NULL COMMENT '1=新增标签 2=新增关系',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=待审 1=通过 2=拒绝 3=过期',
    target_key VARCHAR(191) NOT NULL COMMENT '去重键：标签=归一化名；关系=from:type:to',
    payload JSON NOT NULL COMMENT '提案内容',
    evidence JSON NULL COMMENT '触发证据：文章 ID、查询次数等',
    proposer_type TINYINT NOT NULL COMMENT '1=AI 2=管理员 3=系统',
    proposer_id BIGINT NULL COMMENT '管理员/用户 ID；AI 与系统为空',
    decided_by BIGINT NULL COMMENT '审批人 ID',
    decided_at DATETIME NULL COMMENT '审批时间',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    pending_key VARCHAR(191) GENERATED ALWAYS AS (IF(status = 0, target_key, NULL)) STORED COMMENT '仅待审行参与唯一约束',
    UNIQUE KEY uk_pending (pending_key),
    INDEX idx_status_create (status, create_time)
) ENGINE=InnoDB COMMENT='标签/关系提案';

-- 未收录实体：hit 表按 (归一化名, 来源, 引用) 去重，汇总表只做累加与升级标记
CREATE TABLE unknown_entity (
    id BIGINT PRIMARY KEY COMMENT '雪花 ID',
    name VARCHAR(50) NOT NULL COMMENT '最近一次出现的原始写法',
    name_norm VARCHAR(50) NOT NULL COMMENT '归一化名',
    extract_count INT NOT NULL DEFAULT 0 COMMENT '被抽取命中的不同文章数',
    query_count INT NOT NULL DEFAULT 0 COMMENT '用户查询次数（按用户+天去重）',
    sample_article_id BIGINT NULL COMMENT '最近一条样本文章',
    promoted_proposal_id BIGINT NULL COMMENT '已升级成的提案 ID',
    first_seen DATETIME NOT NULL COMMENT '首次出现时间',
    last_seen DATETIME NOT NULL COMMENT '最近出现时间',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    UNIQUE KEY uk_name_norm (name_norm)
) ENGINE=InnoDB COMMENT='未收录技术实体汇总';

CREATE TABLE unknown_entity_hit (
    id BIGINT PRIMARY KEY COMMENT '雪花 ID',
    name_norm VARCHAR(50) NOT NULL COMMENT '归一化名',
    source TINYINT NOT NULL COMMENT '1=抽取 2=查询',
    ref_key VARCHAR(64) NOT NULL COMMENT '抽取=articleId；查询=userId:yyyyMMdd',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    UNIQUE KEY uk_hit (name_norm, source, ref_key)
) ENGINE=InnoDB COMMENT='未收录实体去重明细';

-- 种子别名：只放真正不同名的写法；SpringBoot/NodeJS 这类靠归一化即可命中，不需要别名
INSERT IGNORE INTO tag_alias (id, tag_id, alias, alias_norm, create_time, update_time)
SELECT v.id, t.id, v.alias, v.alias_norm, NOW(), NOW()
FROM (
    SELECT 1 AS id, 'Kubernetes' AS tag_name, 'k8s' AS alias, 'k8s' AS alias_norm
    UNION ALL SELECT 2, 'Go', 'Golang', 'golang'
    UNION ALL SELECT 3, 'PostgreSQL', 'Postgres', 'postgres'
    UNION ALL SELECT 4, 'Vue.js', 'Vue', 'vue'
    UNION ALL SELECT 5, 'React', 'ReactJS', 'reactjs'
) v
JOIN tag t ON t.name = v.tag_name;

-- 种子关系迁移：来自 knowledge 的 seed-relations.json，共 241 条
-- 端点按 name 关联，标签改名后需要在新库重新执行本段
INSERT IGNORE INTO tag_relation (id, from_tag_id, to_tag_id, relation_type, source, create_time, update_time)
SELECT v.seq, a.id, b.id, v.relation_type, 1, NOW(), NOW()
FROM (
    SELECT 1 AS seq, 'Spring Boot' AS from_name, 'Spring' AS to_name, 'BASED_ON' AS relation_type
    UNION ALL SELECT 2, 'Spring MVC', 'Spring', 'BASED_ON'
    UNION ALL SELECT 3, 'Spring Security', 'Spring', 'BASED_ON'
    UNION ALL SELECT 4, 'Spring Cloud', 'Spring Boot', 'BASED_ON'
    UNION ALL SELECT 5, 'MyBatis', 'Java', 'BASED_ON'
    UNION ALL SELECT 6, 'Hibernate', 'Java', 'BASED_ON'
    UNION ALL SELECT 7, 'Netty', 'Java', 'BASED_ON'
    UNION ALL SELECT 8, 'Dubbo', 'Java', 'BASED_ON'
    UNION ALL SELECT 9, 'Kotlin', 'JVM', 'BASED_ON'
    UNION ALL SELECT 10, 'Scala', 'JVM', 'BASED_ON'
    UNION ALL SELECT 11, 'Groovy', 'JVM', 'BASED_ON'
    UNION ALL SELECT 12, 'TypeScript', 'JavaScript', 'BASED_ON'
    UNION ALL SELECT 13, 'Vue.js', 'JavaScript', 'BASED_ON'
    UNION ALL SELECT 14, 'React', 'JavaScript', 'BASED_ON'
    UNION ALL SELECT 15, 'Angular', 'TypeScript', 'BASED_ON'
    UNION ALL SELECT 16, 'Node.js', 'JavaScript', 'BASED_ON'
    UNION ALL SELECT 17, 'Element Plus', 'Vue.js', 'BASED_ON'
    UNION ALL SELECT 18, '微服务', '分布式', 'BASED_ON'
    UNION ALL SELECT 19, '深度学习', '机器学习', 'BASED_ON'
    UNION ALL SELECT 20, '神经网络', '深度学习', 'BASED_ON'
    UNION ALL SELECT 21, '大模型', '深度学习', 'BASED_ON'
    UNION ALL SELECT 22, 'RAG', 'LLM', 'BASED_ON'
    UNION ALL SELECT 23, 'LangChain', 'LLM', 'BASED_ON'
    UNION ALL SELECT 24, 'Agent', 'LLM', 'BASED_ON'
    UNION ALL SELECT 25, 'Spring Boot', 'Spring MVC', 'USES'
    UNION ALL SELECT 26, 'Spring Boot', 'Spring Security', 'USES'
    UNION ALL SELECT 27, 'Spring Boot', 'MyBatis', 'USES'
    UNION ALL SELECT 28, 'Spring Boot', 'MySQL', 'USES'
    UNION ALL SELECT 29, 'Spring Boot', 'Redis', 'USES'
    UNION ALL SELECT 30, 'Spring Boot', 'RabbitMQ', 'USES'
    UNION ALL SELECT 31, 'Spring Boot', 'Tomcat', 'USES'
    UNION ALL SELECT 32, 'Spring Cloud', 'Nacos', 'USES'
    UNION ALL SELECT 33, 'Spring Cloud', 'Seata', 'USES'
    UNION ALL SELECT 34, 'MyBatis', 'MySQL', 'USES'
    UNION ALL SELECT 35, '后端', 'Spring Boot', 'USES'
    UNION ALL SELECT 36, '后端', 'MySQL', 'USES'
    UNION ALL SELECT 37, '后端', 'Redis', 'USES'
    UNION ALL SELECT 38, '后端', 'RabbitMQ', 'USES'
    UNION ALL SELECT 39, '微服务', 'Nacos', 'USES'
    UNION ALL SELECT 40, '微服务', 'Dubbo', 'USES'
    UNION ALL SELECT 41, '微服务', 'Docker', 'USES'
    UNION ALL SELECT 42, '分布式', '分布式锁', 'USES'
    UNION ALL SELECT 43, '分布式', '消息队列', 'USES'
    UNION ALL SELECT 44, '高并发', '缓存', 'USES'
    UNION ALL SELECT 45, '高并发', '消息队列', 'USES'
    UNION ALL SELECT 46, '高并发', '分布式锁', 'USES'
    UNION ALL SELECT 47, '高并发', '限流', 'USES'
    UNION ALL SELECT 48, '缓存', 'Redis', 'USES'
    UNION ALL SELECT 49, 'Nginx', 'Linux', 'USES'
    UNION ALL SELECT 50, 'Jenkins', 'Git', 'USES'
    UNION ALL SELECT 51, 'CI/CD', 'Jenkins', 'USES'
    UNION ALL SELECT 52, 'DevOps', 'CI/CD', 'USES'
    UNION ALL SELECT 53, '搜索', 'Elasticsearch', 'USES'
    UNION ALL SELECT 54, '知识图谱', 'Neo4j', 'USES'
    UNION ALL SELECT 55, '数据分析', 'SQL', 'USES'
    UNION ALL SELECT 56, '数据分析', 'Python', 'USES'
    UNION ALL SELECT 57, '数据挖掘', '机器学习', 'USES'
    UNION ALL SELECT 58, '数据可视化', '前端', 'USES'
    UNION ALL SELECT 59, '爬虫', 'Python', 'USES'
    UNION ALL SELECT 60, '自然语言处理', '深度学习', 'USES'
    UNION ALL SELECT 61, '计算机视觉', '深度学习', 'USES'
    UNION ALL SELECT 62, '推荐系统', '机器学习', 'USES'
    UNION ALL SELECT 63, 'RAG', '向量数据库', 'USES'
    UNION ALL SELECT 64, 'Agent', '提示词工程', 'USES'
    UNION ALL SELECT 65, 'Agent', 'MCP', 'USES'
    UNION ALL SELECT 66, '游戏开发', 'Unity', 'USES'
    UNION ALL SELECT 67, '小程序', 'JavaScript', 'USES'
    UNION ALL SELECT 68, '游戏开发', 'Lua', 'USES'
    UNION ALL SELECT 69, 'Spring', 'Java', 'PREREQUISITE'
    UNION ALL SELECT 70, 'Spring Boot', 'Spring', 'PREREQUISITE'
    UNION ALL SELECT 71, 'Spring MVC', 'Spring', 'PREREQUISITE'
    UNION ALL SELECT 72, 'Spring Security', 'Spring', 'PREREQUISITE'
    UNION ALL SELECT 73, 'Spring Cloud', 'Spring Boot', 'PREREQUISITE'
    UNION ALL SELECT 74, 'MyBatis', 'Java', 'PREREQUISITE'
    UNION ALL SELECT 75, 'MyBatis', 'SQL', 'PREREQUISITE'
    UNION ALL SELECT 76, 'Hibernate', 'Java', 'PREREQUISITE'
    UNION ALL SELECT 77, 'JVM', 'Java', 'PREREQUISITE'
    UNION ALL SELECT 78, '多线程', 'Java', 'PREREQUISITE'
    UNION ALL SELECT 79, '多线程', '操作系统', 'PREREQUISITE'
    UNION ALL SELECT 80, 'Netty', 'Java', 'PREREQUISITE'
    UNION ALL SELECT 81, 'Netty', '多线程', 'PREREQUISITE'
    UNION ALL SELECT 82, 'Dubbo', 'Java', 'PREREQUISITE'
    UNION ALL SELECT 83, 'Dubbo', '分布式', 'PREREQUISITE'
    UNION ALL SELECT 84, '源码分析', 'Java', 'PREREQUISITE'
    UNION ALL SELECT 85, '设计模式', 'Java', 'PREREQUISITE'
    UNION ALL SELECT 86, 'SQL', '数据库', 'PREREQUISITE'
    UNION ALL SELECT 87, 'MySQL', 'SQL', 'PREREQUISITE'
    UNION ALL SELECT 88, '索引', '数据库', 'PREREQUISITE'
    UNION ALL SELECT 89, '索引', '数据结构', 'PREREQUISITE'
    UNION ALL SELECT 90, '事务', '数据库', 'PREREQUISITE'
    UNION ALL SELECT 91, 'Redis', 'Java', 'PREREQUISITE'
    UNION ALL SELECT 92, 'Neo4j', '数据库', 'PREREQUISITE'
    UNION ALL SELECT 93, '向量数据库', '数据库', 'PREREQUISITE'
    UNION ALL SELECT 94, '知识图谱', '数据库', 'PREREQUISITE'
    UNION ALL SELECT 95, '消息队列', '多线程', 'PREREQUISITE'
    UNION ALL SELECT 96, 'Kafka', '消息队列', 'PREREQUISITE'
    UNION ALL SELECT 97, 'RabbitMQ', '消息队列', 'PREREQUISITE'
    UNION ALL SELECT 98, 'RocketMQ', '消息队列', 'PREREQUISITE'
    UNION ALL SELECT 99, '分布式', '计算机网络', 'PREREQUISITE'
    UNION ALL SELECT 100, '分布式锁', '分布式', 'PREREQUISITE'
    UNION ALL SELECT 101, '分布式锁', '多线程', 'PREREQUISITE'
    UNION ALL SELECT 102, '高并发', '多线程', 'PREREQUISITE'
    UNION ALL SELECT 103, '限流', '高并发', 'PREREQUISITE'
    UNION ALL SELECT 104, '微服务', '分布式', 'PREREQUISITE'
    UNION ALL SELECT 105, 'Nacos', '分布式', 'PREREQUISITE'
    UNION ALL SELECT 106, 'Seata', '分布式', 'PREREQUISITE'
    UNION ALL SELECT 107, 'Seata', '事务', 'PREREQUISITE'
    UNION ALL SELECT 108, 'Zookeeper', '分布式', 'PREREQUISITE'
    UNION ALL SELECT 109, '架构设计', '设计模式', 'PREREQUISITE'
    UNION ALL SELECT 110, '架构设计', '分布式', 'PREREQUISITE'
    UNION ALL SELECT 111, '架构设计', '高并发', 'PREREQUISITE'
    UNION ALL SELECT 112, '性能优化', 'JVM', 'PREREQUISITE'
    UNION ALL SELECT 113, '性能优化', '数据库', 'PREREQUISITE'
    UNION ALL SELECT 114, 'Linux', '操作系统', 'PREREQUISITE'
    UNION ALL SELECT 115, 'Shell', 'Linux', 'PREREQUISITE'
    UNION ALL SELECT 116, '运维', 'Linux', 'PREREQUISITE'
    UNION ALL SELECT 117, 'Docker', 'Linux', 'PREREQUISITE'
    UNION ALL SELECT 118, 'Kubernetes', 'Docker', 'PREREQUISITE'
    UNION ALL SELECT 119, '云原生', 'Docker', 'PREREQUISITE'
    UNION ALL SELECT 120, '云原生', 'Kubernetes', 'PREREQUISITE'
    UNION ALL SELECT 121, 'Nginx', '计算机网络', 'PREREQUISITE'
    UNION ALL SELECT 122, 'CI/CD', 'Git', 'PREREQUISITE'
    UNION ALL SELECT 123, 'CI/CD', 'Linux', 'PREREQUISITE'
    UNION ALL SELECT 124, 'Jenkins', 'CI/CD', 'PREREQUISITE'
    UNION ALL SELECT 125, 'XXL-JOB', 'Java', 'PREREQUISITE'
    UNION ALL SELECT 126, '算法', '数据结构', 'PREREQUISITE'
    UNION ALL SELECT 127, '操作系统', '计算机组成原理', 'PREREQUISITE'
    UNION ALL SELECT 128, '编译原理', '数据结构', 'PREREQUISITE'
    UNION ALL SELECT 129, '汇编', '计算机组成原理', 'PREREQUISITE'
    UNION ALL SELECT 130, '网络安全', '计算机网络', 'PREREQUISITE'
    UNION ALL SELECT 131, '后端', '计算机网络', 'PREREQUISITE'
    UNION ALL SELECT 132, '后端', '操作系统', 'PREREQUISITE'
    UNION ALL SELECT 133, '前端', 'HTML', 'PREREQUISITE'
    UNION ALL SELECT 134, '前端', 'CSS', 'PREREQUISITE'
    UNION ALL SELECT 135, '前端', 'JavaScript', 'PREREQUISITE'
    UNION ALL SELECT 136, 'TypeScript', 'JavaScript', 'PREREQUISITE'
    UNION ALL SELECT 137, 'Vue.js', 'JavaScript', 'PREREQUISITE'
    UNION ALL SELECT 138, 'React', 'JavaScript', 'PREREQUISITE'
    UNION ALL SELECT 139, 'Angular', 'TypeScript', 'PREREQUISITE'
    UNION ALL SELECT 140, 'Node.js', 'JavaScript', 'PREREQUISITE'
    UNION ALL SELECT 141, 'Webpack', 'Node.js', 'PREREQUISITE'
    UNION ALL SELECT 142, 'Vite', 'Node.js', 'PREREQUISITE'
    UNION ALL SELECT 143, 'Element Plus', 'Vue.js', 'PREREQUISITE'
    UNION ALL SELECT 144, '小程序', '前端', 'PREREQUISITE'
    UNION ALL SELECT 145, 'Android', 'Kotlin', 'PREREQUISITE'
    UNION ALL SELECT 146, 'iOS', 'Swift', 'PREREQUISITE'
    UNION ALL SELECT 147, 'Unity', 'C#', 'PREREQUISITE'
    UNION ALL SELECT 148, '游戏开发', '图形学', 'PREREQUISITE'
    UNION ALL SELECT 149, '图形学', 'C++', 'PREREQUISITE'
    UNION ALL SELECT 150, '嵌入式', 'C', 'PREREQUISITE'
    UNION ALL SELECT 151, '物联网', '嵌入式', 'PREREQUISITE'
    UNION ALL SELECT 152, '物联网', '计算机网络', 'PREREQUISITE'
    UNION ALL SELECT 153, '区块链', '分布式', 'PREREQUISITE'
    UNION ALL SELECT 154, '区块链', '计算机网络', 'PREREQUISITE'
    UNION ALL SELECT 155, '机器学习', 'Python', 'PREREQUISITE'
    UNION ALL SELECT 156, '机器学习', '算法', 'PREREQUISITE'
    UNION ALL SELECT 157, '深度学习', '机器学习', 'PREREQUISITE'
    UNION ALL SELECT 158, '神经网络', '机器学习', 'PREREQUISITE'
    UNION ALL SELECT 159, '大模型', '深度学习', 'PREREQUISITE'
    UNION ALL SELECT 160, '自然语言处理', '机器学习', 'PREREQUISITE'
    UNION ALL SELECT 161, 'RAG', 'LLM', 'PREREQUISITE'
    UNION ALL SELECT 162, '提示词工程', 'LLM', 'PREREQUISITE'
    UNION ALL SELECT 163, 'Agent', 'LLM', 'PREREQUISITE'
    UNION ALL SELECT 164, 'MCP', 'Agent', 'PREREQUISITE'
    UNION ALL SELECT 165, 'LangChain', 'LLM', 'PREREQUISITE'
    UNION ALL SELECT 166, 'LangChain', 'Python', 'PREREQUISITE'
    UNION ALL SELECT 167, 'PyTorch', '深度学习', 'PREREQUISITE'
    UNION ALL SELECT 168, 'PyTorch', 'Python', 'PREREQUISITE'
    UNION ALL SELECT 169, 'TensorFlow', '深度学习', 'PREREQUISITE'
    UNION ALL SELECT 170, 'TensorFlow', 'Python', 'PREREQUISITE'
    UNION ALL SELECT 171, '数据挖掘', 'SQL', 'PREREQUISITE'
    UNION ALL SELECT 172, '爬虫', '计算机网络', 'PREREQUISITE'
    UNION ALL SELECT 173, '开源', 'Git', 'PREREQUISITE'
    UNION ALL SELECT 174, 'MySQL', 'PostgreSQL', 'SIMILAR_TO'
    UNION ALL SELECT 175, 'PostgreSQL', 'Oracle', 'SIMILAR_TO'
    UNION ALL SELECT 176, 'Oracle', 'SQL Server', 'SIMILAR_TO'
    UNION ALL SELECT 177, 'Kafka', 'RabbitMQ', 'SIMILAR_TO'
    UNION ALL SELECT 178, 'Kafka', 'RocketMQ', 'SIMILAR_TO'
    UNION ALL SELECT 179, 'RabbitMQ', 'RocketMQ', 'SIMILAR_TO'
    UNION ALL SELECT 180, 'Vue.js', 'React', 'SIMILAR_TO'
    UNION ALL SELECT 181, 'Vue.js', 'Angular', 'SIMILAR_TO'
    UNION ALL SELECT 182, 'React', 'Angular', 'SIMILAR_TO'
    UNION ALL SELECT 183, 'Webpack', 'Vite', 'SIMILAR_TO'
    UNION ALL SELECT 184, 'MyBatis', 'Hibernate', 'SIMILAR_TO'
    UNION ALL SELECT 185, 'Dubbo', 'Spring Cloud', 'SIMILAR_TO'
    UNION ALL SELECT 186, 'Nacos', 'Zookeeper', 'SIMILAR_TO'
    UNION ALL SELECT 187, 'PyTorch', 'TensorFlow', 'SIMILAR_TO'
    UNION ALL SELECT 188, '大模型', 'LLM', 'SIMILAR_TO'
    UNION ALL SELECT 189, 'Go', 'Rust', 'SIMILAR_TO'
    UNION ALL SELECT 190, 'PHP', 'Ruby', 'SIMILAR_TO'
    UNION ALL SELECT 191, 'MySQL', '数据库', 'BELONGS_TO'
    UNION ALL SELECT 192, 'PostgreSQL', '数据库', 'BELONGS_TO'
    UNION ALL SELECT 193, 'Oracle', '数据库', 'BELONGS_TO'
    UNION ALL SELECT 194, 'SQL Server', '数据库', 'BELONGS_TO'
    UNION ALL SELECT 195, 'SQLite', '数据库', 'BELONGS_TO'
    UNION ALL SELECT 196, 'MongoDB', '数据库', 'BELONGS_TO'
    UNION ALL SELECT 197, 'ClickHouse', '数据库', 'BELONGS_TO'
    UNION ALL SELECT 198, 'Neo4j', '数据库', 'BELONGS_TO'
    UNION ALL SELECT 199, '向量数据库', '数据库', 'BELONGS_TO'
    UNION ALL SELECT 200, '索引', '数据库', 'BELONGS_TO'
    UNION ALL SELECT 201, '事务', '数据库', 'BELONGS_TO'
    UNION ALL SELECT 202, 'Elasticsearch', '搜索', 'BELONGS_TO'
    UNION ALL SELECT 203, 'Kafka', '消息队列', 'BELONGS_TO'
    UNION ALL SELECT 204, 'RabbitMQ', '消息队列', 'BELONGS_TO'
    UNION ALL SELECT 205, 'RocketMQ', '消息队列', 'BELONGS_TO'
    UNION ALL SELECT 206, 'Nacos', '微服务', 'BELONGS_TO'
    UNION ALL SELECT 207, 'Seata', '分布式', 'BELONGS_TO'
    UNION ALL SELECT 208, 'Zookeeper', '分布式', 'BELONGS_TO'
    UNION ALL SELECT 209, '分布式锁', '分布式', 'BELONGS_TO'
    UNION ALL SELECT 210, '限流', '高并发', 'BELONGS_TO'
    UNION ALL SELECT 211, '缓存', '性能优化', 'BELONGS_TO'
    UNION ALL SELECT 212, 'Docker', '云原生', 'BELONGS_TO'
    UNION ALL SELECT 213, 'Kubernetes', '云原生', 'BELONGS_TO'
    UNION ALL SELECT 214, 'Jenkins', 'CI/CD', 'BELONGS_TO'
    UNION ALL SELECT 215, 'XXL-JOB', '运维', 'BELONGS_TO'
    UNION ALL SELECT 216, 'DevOps', '运维', 'BELONGS_TO'
    UNION ALL SELECT 217, 'Nginx', '后端', 'BELONGS_TO'
    UNION ALL SELECT 218, 'Tomcat', '后端', 'BELONGS_TO'
    UNION ALL SELECT 219, 'Spring', '后端', 'BELONGS_TO'
    UNION ALL SELECT 220, 'Spring Boot', '后端', 'BELONGS_TO'
    UNION ALL SELECT 221, 'MyBatis', '后端', 'BELONGS_TO'
    UNION ALL SELECT 222, 'Hibernate', '后端', 'BELONGS_TO'
    UNION ALL SELECT 223, 'Vue.js', '前端', 'BELONGS_TO'
    UNION ALL SELECT 224, 'React', '前端', 'BELONGS_TO'
    UNION ALL SELECT 225, 'Angular', '前端', 'BELONGS_TO'
    UNION ALL SELECT 226, 'Webpack', '前端', 'BELONGS_TO'
    UNION ALL SELECT 227, 'Vite', '前端', 'BELONGS_TO'
    UNION ALL SELECT 228, '小程序', '前端', 'BELONGS_TO'
    UNION ALL SELECT 229, 'Android', '移动开发', 'BELONGS_TO'
    UNION ALL SELECT 230, 'iOS', '移动开发', 'BELONGS_TO'
    UNION ALL SELECT 231, 'Unity', '游戏开发', 'BELONGS_TO'
    UNION ALL SELECT 232, 'PyTorch', '深度学习', 'BELONGS_TO'
    UNION ALL SELECT 233, 'TensorFlow', '深度学习', 'BELONGS_TO'
    UNION ALL SELECT 234, '设计模式', '软件工程', 'BELONGS_TO'
    UNION ALL SELECT 235, '单元测试', '软件工程', 'BELONGS_TO'
    UNION ALL SELECT 236, 'Go', '后端', 'BELONGS_TO'
    UNION ALL SELECT 237, 'PHP', '后端', 'BELONGS_TO'
    UNION ALL SELECT 238, 'Ruby', '后端', 'BELONGS_TO'
    UNION ALL SELECT 239, 'RESTful', '后端', 'BELONGS_TO'
    UNION ALL SELECT 240, '机器学习', '人工智能', 'BELONGS_TO'
    UNION ALL SELECT 241, '深度学习', '人工智能', 'BELONGS_TO'
) v
JOIN tag a ON a.name = v.from_name
JOIN tag b ON b.name = v.to_name;

-- 验收：应分别得到 241 与 5
-- SELECT COUNT(*) FROM tag_relation WHERE source = 1;
-- SELECT COUNT(*) FROM tag_alias;
