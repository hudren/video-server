function showVideo() {
  // Show player
  var block = document.getElementById("player");
  if (block) {
    block.style.visibility = "visible";
  }
}

function hideVideo() {
  var vid = document.getElementsByTagName('video')[0];
  if (vid) {
    vid.addEventListener("canplaythrough", showVideo, true);
  }

  // Safari needs help
  window.setTimeout(showVideo, 500);
}

(function () {
  // Add 'js' class to head element if Javascript is enabled
  document.documentElement.className += " js";
}());
