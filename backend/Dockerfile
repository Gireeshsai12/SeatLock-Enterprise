FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/seatlock-1.0.0.jar /app/seatlock.jar
USER 10001:10001
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app/seatlock.jar"]
