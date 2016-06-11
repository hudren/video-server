(ns video-server.html.pages
  (:require [video-server.html.site :refer :all]))

(defn downloads-template
  [host apk]
  (site-template
    {:title "Videos@Home Downloads" :toolbar (toolbar "Downloads")}
    [:h3 "Android"]
    [:p "Note: Make sure your Android settings allow the installation of applications from unknown sources."]
    [:p "If the installation does not start automatically after downloading, try one of the following:"]
    [:ul
     [:li "Select the download notification to start the installation"]
     [:li "Select the video-client-release.apk file within the Downloads app"]
     [:li "Use a file manager to open the .apk from the Downloads directory"]]
    [:p "Message or text: Download the Videos@Home app from " (str host "/" apk)]
    [:div {:class "actions"}
     [:a {:href "video-client-release.apk" :type "application/vnd.android.package-archive"}
      [:button.mdl-button.mdl-js-button.mdl-button--raised.mdl-js-ripple-effect.mdl-button--accent
       [:i.material-icons "file_download"] " Download App"]]]))

(defn legal-template
  []
  (site-template
    {:title "Videos@Home Legal" :toolbar (toolbar "Legal")}
    [:h3 "Metadata"]
    [:p "Metadata may be obtained from one or more of the following websites:"]
    [:ul
     [:li
      [:h4 (blank-link "https://www.freebase.com" "Freebase")]
      [:p (blank-link "http://creativecommons.org/licenses/by/2.5"
                      "Creative Commons Attribution Only (or CC-BY) license")]]
     [:li
      [:h4 (blank-link "https://www.omdbapi.com" "OMDb")]
      [:p (blank-link "http://creativecommons.org/licenses/by/4.0"
                      "Creative Commons Attribute 4.0 International License")]]
     [:li
      [:h4 (blank-link "http://thetvdb.com" "TheTVDB.com")]
      [:p (blank-link "http://creativecommons.org/licenses/by-nc/4.0"
                      "Creative Commons Attribution-NonCommercial 4.0 International License")]]
     [:li
      [:h4 (blank-link "http://www.themoviedb.org" "themoviedb.org")]
      [:p "This product uses the TMDb API but is not endorsed or certified by TMDb."]]]))

(defn not-found-template
  []
  (site-template
    {:title "Videos@Home Not Found" :toolbar "Videos@Home"}
    [:h3 "Not Found"]
    [:p "The requested page could not be found. Return to the main " (inline-link "/" "title page") "."]))

