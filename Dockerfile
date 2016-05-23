FROM fedora:23

ENV JAVA_HOME /usr/java/latest

COPY mkclean-0.8.7-1.1.x86_64.rpm /

RUN dnf update -y && \
  rpm -Uvh http://download1.rpmfusion.org/free/fedora/rpmfusion-free-release-$(rpm -E %fedora).noarch.rpm && \
  rpm -Uvh http://download1.rpmfusion.org/nonfree/fedora/rpmfusion-nonfree-release-$(rpm -E %fedora).noarch.rpm && \
  dnf install -y java-1.8.0 which ffmpeg mkvtoolnix && \
  rpm -Uvh mkclean-0.8.7-1.1.x86_64.rpm && \
  rm /mkclean*

COPY docker/video-server /
COPY docker/settings.edn /
COPY target/video-server.jar /

VOLUME /Movies
VOLUME /Volumes
EXPOSE 8090 8394/udp
ENTRYPOINT ["/video-server"]
