FROM hudren/video-base

COPY docker/video-server /
COPY docker/settings.edn /
COPY target/video-server.jar /

VOLUME /Movies
VOLUME /Volumes
EXPOSE 8090 8394/udp
ENTRYPOINT ["/video-server"]
