# ============================================================
# 智能广告基座学习平台 · 后端镜像（若依 4.8.3 多模块）
# 多阶段构建：Maven 17 构建 ruoyi-admin → JRE 17 运行
# 构建上下文：项目根目录（edu-learning-platform/）
# ============================================================
# ---------- 阶段 1：Maven 构建 ----------
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
# 限制构建堆，避免 OOM
ENV MAVEN_OPTS="-Xmx1024m"
# 先拷贝所有 pom.xml 预热依赖，利用 Docker 层缓存
COPY pom.xml .
COPY ruoyi-admin/pom.xml ruoyi-admin/
COPY ruoyi-common/pom.xml ruoyi-common/
COPY ruoyi-framework/pom.xml ruoyi-framework/
COPY ruoyi-system/pom.xml ruoyi-system/
COPY ruoyi-quartz/pom.xml ruoyi-quartz/
COPY ruoyi-generator/pom.xml ruoyi-generator/
COPY learning-community/pom.xml learning-community/
COPY learning-course/pom.xml learning-course/
COPY learning-interation/pom.xml learning-interation/
COPY learning-user/pom.xml learning-user/
COPY learning-video/pom.xml learning-video/
COPY learning-api/pom.xml learning-api/
# 使用阿里云镜像，绕开 Maven 3.9 的 HTTP 仓库拦截，加速依赖下载
COPY deploy/maven-settings.xml /build/maven-settings.xml
RUN mvn -s /build/maven-settings.xml -B dependency:go-offline
# 拷贝源码并打包（跳过测试）
COPY . .
RUN mvn -s /build/maven-settings.xml -B clean package -pl ruoyi-admin -am -DskipTests
# ---------- 阶段 2：JRE 运行 ----------
FROM eclipse-temurin:17-jre
WORKDIR /app
# 健康检查需要 curl；验证码（Kaptcha）渲染需要字体
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*
COPY --from=builder /build/ruoyi-admin/target/*.jar app.jar
ENV TZ=Asia/Shanghai
EXPOSE 80
ENTRYPOINT ["java", "-jar", "app.jar"]
