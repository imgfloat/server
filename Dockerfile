FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml ./
COPY .git ./.git
RUN mvn -B dependency:go-offline
COPY src ./src
RUN rm -rf src/test
RUN mvn -B package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/imgfloat-*.jar app.jar
COPY package.json ./
EXPOSE 8080 8443
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar app.jar"]
