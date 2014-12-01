(defproject video-server "0.1.0-SNAPSHOT"
  :description "Videos@Home server"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.clojure/data.json "0.2.5"]
                 [org.clojure/tools.cli "0.3.1"]
                 [org.clojure/tools.logging "0.3.1"]
                 [ch.qos.logback/logback-classic "1.1.2"]
                 [org.eclipse.jetty/jetty-server "9.2.3.v20140905"]
                 [org.eclipse.jetty/jetty-servlet "9.2.3.v20140905"]
                 [org.eclipse.jetty/jetty-servlets "9.2.3.v20140905"]
                 [clojure-watch "0.1.10"]
                 [ring/ring-core "1.3.1"]
                 [ring/ring-servlet "1.3.1"]
                 [bk/ring-gzip "0.1.1"]
                 [compojure "1.2.1"]
                 [enlive "1.1.5"]]

  :main video-server.main
  :aot [video-server.main]
  :uberjar-name "videos@home.jar"

  :profiles {:dev {:source-paths ["dev"]}}

  :aliases {"docs" ["do" "marg" "-m," "shell" "open" "docs/toc.html"]})
