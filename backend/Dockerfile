FROM maven:3.8.8-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# preferIPv4Stack: many cloud/container networks (Render included) don't route outbound IPv6
# properly, so the JVM's default IPv6-first resolution of hosts like smtp.gmail.com just hangs
# until timeout instead of falling back to IPv4 - this makes it try IPv4 first.
ENTRYPOINT ["java", "-Djava.net.preferIPv4Stack=true", "-jar", "app.jar"]