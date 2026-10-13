package com.wisdomhub.knowledge.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动时执行 resources/neo4j/constraints.cypher 里的唯一约束
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Neo4jConstraintInitializer implements ApplicationRunner {

    private static final String CONSTRAINTS_RESOURCE = "neo4j/constraints.cypher";

    private final Driver driver;

    @Override
    public void run(ApplicationArguments args) {
        for (String statement : loadStatements()) {
            createConstraint(statement);
        }
    }

    /**
     * 一行一条语句，空行与 '#' 起头的注释跳过。
     */
    private List<String> loadStatements() {
        List<String> statements = new ArrayList<>();
        try (InputStream in = new ClassPathResource(CONSTRAINTS_RESOURCE).getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String statement = line.trim();
                if (!statement.isEmpty() && !statement.startsWith("#")) {
                    statements.add(statement);
                }
            }
        } catch (Exception e) {
            log.error("读取 Neo4j 约束脚本失败: {}", CONSTRAINTS_RESOURCE, e);
        }
        return statements;
    }

    /**
     * Neo4j 不可达时只记日志不抛。
     */
    private void createConstraint(String statement) {
        try (Session session = driver.session()) {
            session.run(statement).consume();
            log.info("Neo4j 约束已建或已存在: {}", statement);
        } catch (Exception e) {
            log.error("Neo4j 约束初始化失败（检查 Neo4j 是否可达、同名约束是否定义不同）: {}", statement, e);
        }
    }
}
