FROM java
MAINTAINER Jeff Hudren <hudren@gmail.com>

COPY mkclean-0.8.7-1.1.x86_64.rpm /

RUN dnf update -y && \
  #rpm --import http://li.nux.ro/download/nux/RPM-GPG-KEY-nux.ro && \
  #rpm -Uvh http://li.nux.ro/download/nux/dextop/el7/x86_64/nux-dextop-release-0-1.el7.nux.noarch.rpm && \
  dnf install -y ffmpeg mkvtoolnix && \
  rpm -Uvh mkclean-0.8.7-1.1.x86_64.rpm && \
  rm /mkclean*
