FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --chown=1000:1000 app/build/libs/app-1.0.0.jar /app/app.jar

USER 1000:1000

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
