# Videos@Home

The Videos@Home project is designed to stream, download, and cast high quality
videos to your notebooks, mobile devices, and televisions. Emphasis is placed
on simplicity, specifically targetting modern video formats with good hardware
decoding.

Features include:

* Streaming 1080p full Bluray quality video to your Android devices
* Downloading videos and subtitles to your device
* Casting to Google cast devices (including vtt subtitles)
* Playback on Chromebooks and other browsers
* Automatic encoding of videos for downloading and casting

This software is intended to be used over your local (high bandwidth) network
only as on-the-fly encoding and adaptive bitrate streaming are not supported.

## Prerequisites

The following software is required to build and run this software:

* Java 7
* Leiningen 2
* Bower

The following runtime dependencies are also required:

* ffmpeg
* mkvtoolnix (optional)
* mkclean (recommended)

## Usage

This project implements the server which is built and started using the
`video-server` script taking an optional argument specifying the folder from
which to serve videos.

Use the `video-server --help` command for more options.

When the server is initially started, it may queue up encodes to enable casting
and downloading of the videos. The encoded videos will be placed in the same
folder and appear as the same title. The client application will choose the
appropriate file for streaming, downloading or casting.

This folder is watched for new videos to be added to the library. This software
has primarily been tested for matroska files created from discs as the primary
source for encoding.

## License

Copyright © 2014 Jeff Hudren. All rights reserved.

Distributed under the Eclipse Public License either version 1.0 or (at your
option) any later version.
