package com.wisdomhub.common.core.generator;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.config.rules.DateType;
import com.baomidou.mybatisplus.generator.engine.VelocityTemplateEngine;
import com.baomidou.mybatisplus.generator.fill.Column;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 跑一次、改一次常量，然后把生成结果当草稿改。
 *
 * <pre>
 * mvn -q exec:java -pl wisdomhub-common/common-core
 * </pre>
 *
 * 已存在的文件默认不会被覆盖，所以可以放心对已有表重跑。
 */
public final class CodeGenerator {

    /** 目标模块目录名，仓库根目录下的那个。 */
    private static final String MODULE = "wisdomhub-user";
    /** 模块的包后缀，com.wisdomhub.&lt;这个&gt;。 */
    private static final String PACKAGE = "user";
    /** 要生成哪些表，表名。 */
    private static final List<String> TABLES = List.of("user_follow");

    private static final String DATABASE = "wisdomhub_user";
    /** 时区参数与会话时区一起钉死成 UTC。 */
    private static final String URL = "jdbc:mysql://127.0.0.1:3306/" + DATABASE
            + "?characterEncoding=utf8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true";
    private static final String USERNAME = "root";

    private CodeGenerator() {
    }

    public static void main(String[] args) {
        Path moduleDir = repoRoot().resolve(MODULE);

        FastAutoGenerator.create(URL, USERNAME, mysqlPassword())
                .globalConfig(b -> b
                        .author("PulsarHaze")
                        .outputDir(moduleDir.resolve("src/main/java").toString())
                        .disableOpenDir()
                        .dateType(DateType.TIME_PACK)
                        .commentDate("yyyy-MM-dd"))
                .dataSourceConfig(b -> b.driverClassName("com.mysql.cj.jdbc.Driver"))
                .packageConfig(b -> b
                        .parent("com.wisdomhub")
                        .moduleName(PACKAGE)
                        .entity("entity")
                        .mapper("mapper")
                        .service("service")
                        .serviceImpl("service.impl")
                        .controller("controller")
                        .xml("mapper.xml")
                        // 不指定的话 XML 会落在 src/main/java 下，Maven 不会打包它
                        .pathInfo(Map.of(OutputFile.xml,
                                moduleDir.resolve("src/main/resources/mapper").toString())))
                .strategyConfig(b -> b
                        .addInclude(TABLES)
                        .entityBuilder()
                        .enableLombok()
                        .idType(IdType.ASSIGN_ID)
                        .addTableFills(
                                new Column("create_time", FieldFill.INSERT),
                                new Column("update_time", FieldFill.INSERT_UPDATE))
                        .controllerBuilder()
                        .enableRestStyle()
                        .serviceBuilder()
                        .formatServiceFileName("%sService")
                        .formatServiceImplFileName("%sServiceImpl")
                        .mapperBuilder()
                        .enableBaseResultMap()
                        .enableBaseColumnList())
                .templateEngine(new VelocityTemplateEngine())
                .execute();
    }

    /** exec:java 的工作目录是 common-core，从 IDE 跑可能是仓库根目录，两种都要能对上。 */
    private static Path repoRoot() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.isDirectory(dir.resolve("wisdomhub-common"))) {
                return dir;
            }
        }
        throw new IllegalStateException("没找到仓库根目录，当前目录： " + Path.of("").toAbsolutePath());
    }

    /** 密码不进源码，依次取系统属性、环境变量、仓库根目录的 .env。 */
    private static String mysqlPassword() {
        String password = System.getProperty("mysql.password");
        if (password == null || password.isBlank()) {
            password = System.getenv("MYSQL_ROOT_PASSWORD");
        }
        if (password == null || password.isBlank()) {
            password = dotEnvValue(repoRoot().resolve(".env"), "MYSQL_ROOT_PASSWORD");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "取不到数据库密码：给 -Dmysql.password=、环境变量 MYSQL_ROOT_PASSWORD，或在仓库根目录 .env 里配");
        }
        return password;
    }

    private static String dotEnvValue(Path envFile, String key) {
        if (!Files.isRegularFile(envFile)) {
            return null;
        }
        try {
            for (String line : Files.readAllLines(envFile)) {
                String trimmed = line.trim();
                if (trimmed.startsWith(key + "=")) {
                    return trimmed.substring(key.length() + 1).trim();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }
}
