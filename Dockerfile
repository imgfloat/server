FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/imgfloat-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080 8443
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $$JAVA_OPTS -jar app.jar"]
