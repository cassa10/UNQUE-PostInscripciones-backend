FROM openjdk:8-alpine

COPY ./build/libs/postinscripciones-0.0.1-SNAPSHOT.jar app.jar

ENTRYPOINT ["java","-jar","/app.jar"]