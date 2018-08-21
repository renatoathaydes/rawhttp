FROM findepi/graalvm AS rawhttp-native

WORKDIR /app
ENV APP_NAME rawhttp

ADD build/libs/${APP_NAME}.jar /app

RUN apt-get update && apt-get install -y gcc zlib1g-dev

RUN native-image -jar ${APP_NAME}.jar --static

# Create minimal executable image with the native rawhttp command

FROM busybox:glibc

COPY --from=rawhttp-native /app/rawhttp rawhttp

ENTRYPOINT ["/rawhttp"]
