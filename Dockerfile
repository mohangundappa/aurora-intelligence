FROM maven:3.9-eclipse-temurin-21 AS build
ARG MAVEN_MIRROR_URL=""
WORKDIR /src
COPY . .
RUN if [ -n "$MAVEN_MIRROR_URL" ]; then mkdir -p /root/.m2 && printf '<settings><mirrors><mirror><id>optional</id><mirrorOf>central</mirrorOf><url>%s</url></mirror></mirrors></settings>' "$MAVEN_MIRROR_URL" > /root/.m2/settings.xml; fi && mvn -q -pl app -am package -DskipTests
FROM eclipse-temurin:21-jre
COPY --from=build /src/app/target/app-0.1.0-SNAPSHOT.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
