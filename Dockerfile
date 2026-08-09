FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
RUN mkdir -p /root/.m2 && printf '%s' '<settings><mirrors><mirror><id>mirror</id><mirrorOf>central</mirrorOf><url>https://repo.huaweicloud.com/repository/maven/</url></mirror></mirrors></settings>' > /root/.m2/settings.xml
COPY . .
RUN mvn -q -pl app -am package -DskipTests
FROM eclipse-temurin:21-jre
COPY --from=build /src/app/target/app-0.1.0-SNAPSHOT.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
