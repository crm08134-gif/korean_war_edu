# 백엔드 이미지. data/ 폴더가 필요해서 저장소 맨 위에서 빌드한다:
#   docker build -f backend/Dockerfile -t korean-war-backend .

# 1단계: JDK로 jar를 만든다
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
# build.gradle이 ../data의 전선·사건·사진을 jar에 넣으므로 둘 다 복사한다
COPY backend backend
COPY data data
WORKDIR /src/backend
# 테스트는 CI가 돌리므로 여기서는 실행할 jar만 만든다
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

# 2단계: JRE만 있는 작은 이미지에서 실행한다
FROM eclipse-temurin:25-jre
WORKDIR /app
# root가 아닌 사용자로 실행한다 (컨테이너가 뚫려도 피해를 줄인다)
RUN useradd --system --no-create-home app
COPY --from=build /src/backend/build/libs/*-SNAPSHOT.jar app.jar
USER app
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
# 2GB 서버에서 프론트·DB와 메모리를 나눠 쓰므로 힙 상한을 512MB로 둔다
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
