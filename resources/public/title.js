"use strict";

function showControls() {
  var vid = document.getElementsByTagName('video')[0];
  if (vid) {
    vid.setAttribute("controls", "true")
  }
}

function showVideo() {
  // Show player
  var block = document.getElementById("player");
  if (block) {
    block.style.visibility = "visible";
  }

  // Chrome needs a delay
  window.setTimeout(showControls, 500)
  window.setTimeout(showControls, 1000)
  window.setTimeout(showControls, 2000)
  window.setTimeout(showControls, 5000)
}

function hideVideo() {
  var vid = document.getElementsByTagName("video")[0];
  if (vid) {
    vid.addEventListener("canplay", showVideo);

    // Hide controls
    // vid.removeAttribute("controls")
  }

  // Safari needs help
  window.setTimeout(showVideo, 500);
}

// Add 'js' class to head element if Javascript is enabled
(function () {
  document.documentElement.className += " js";
}());
