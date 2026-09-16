# 统一的 Java 服务 Dockerfile（多阶段构建）。
# 构建参数 SERVICE 指定模块名，四个服务共用同一份 Dockerfile —— 避免四份只有一行差异的复制粘贴。
#
#   docker build -f Dockerfile --build-arg SERVICE=em-device-access -t energy/device-access:1.0 .
#
# 注意：基础镜像用 JRE 而非 JDK（运行时不需要编译器，镜像小 ~200MB）；
# 用国内镜像源加速；容器内时区固定为 Asia/Shanghai，否则日志与 collect_time 会差 8 小时。

# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先只拷贝 POM 做依赖解析（利用 Docker 层缓存：POM 不变时依赖层直接复用，
# 源码改动不需要重新下载全部依赖 —— 这是多阶段 + 分层拷贝的核心收益）
COPY pom.xml .
COPY em-common/pom.xml em-common/
COPY em-gateway/pom.xml em-gateway/
COPY em-device-access/pom.xml em-device-access/
COPY em-data-process/pom.xml em-data-process/
COPY em-web-api/pom.xml em-web-api/
COPY em-simulator/pom.xml em-simulator/
RUN mvn -B dependency:go-offline -DskipTests -q || true

# 再拷源码做真正构建
COPY em-common em-common
COPY em-gateway em-gateway
COPY em-device-access em-device-access
COPY em-data-process em-data-process
COPY em-web-api em-web-api
COPY em-simulator em-simulator
ARG SERVICE
RUN mvn -B package -DskipTests -pl ${SERVICE} -am -q

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre
ARG SERVICE
WORKDIR /app

COPY --from=build /build/${SERVICE}/target/${SERVICE}-1.0.0-SNAPSHOT.jar app.jar

ENV TZ=Asia/Shanghai JAVA_OPTS="-Xms256m -Xmx512m"

# 容器内清掉宿主可能注入的 SERVER__PORT，防止 relaxed binding 把端口解析错
ENV SERVER__PORT="" SERVER__HOST=""

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
