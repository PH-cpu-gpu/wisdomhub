# 9 个 Java 服务共用的运行镜像。
# 只负责 run：jar 由开发机 mvn 打好，构建上下文是各模块目录。
#   docker build -f docker/java/Dockerfile --build-arg JAR_FILE=target/wisdomhub-user-1.0.0-SNAPSHOT.jar wisdomhub-user
# JVM 参数全部由 JAVA_TOOL_OPTIONS 注入，见 docker-compose.prod.yml。

FROM eclipse-temurin:21-jre-alpine

# TZ=Asia/Shanghai 依赖 tzdata，alpine 默认没带
RUN apk add --no-cache tzdata

ARG JAR_FILE
WORKDIR /app

RUN addgroup -S app && adduser -S -G app app
COPY --chown=app:app ${JAR_FILE} /app/app.jar

USER app

# -XX:+AutoCreateSharedArchive 往 /tmp/app.jsa 写，容器重建后丢一次即可
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
