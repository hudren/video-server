FROM ffmpeg
MAINTAINER Jeff Hudren <hudren@gmail.com>

RUN dnf install -y which

COPY video-server /
COPY settings.edn /
COPY video-server.jar /

VOLUME /Movies
VOLUME /Volumes
EXPOSE 8090 8394/udp
ENTRYPOINT ["/video-server"]
