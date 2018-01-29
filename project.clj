(defproject com.hudren.homevideo/video-server "0.5.11"
  :description "Videos@Home server"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/core.async "0.4.474"]
                 [org.clojure/core.cache "0.6.5"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/data.zip "0.1.2"]
                 [org.clojure/test.check  "0.9.0"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/tools.logging "0.4.0"]
                 [ch.qos.logback/logback-classic "1.2.3"]
                 [trptcolin/versioneer "0.2.0"]
                 [net.dongliu/apk-parser "2.5.0"]
                 [clj-http "3.7.0"]
                 [org.eclipse.jetty/jetty-server "9.2.21.v20170120"]
                 [org.eclipse.jetty/jetty-servlet "9.2.21.v20170120"]
                 [org.eclipse.jetty/jetty-servlets "9.2.21.v20170120"]
                 [javax.servlet/javax.servlet-api "3.1.0"]
                 [ring/ring-core "1.6.3"]
                 [ring/ring-devel "1.6.3"]
                 [ring/ring-servlet "1.6.3" :exclusions [javax.servlet/servlet-api]]
                 [ring/ring-defaults "0.3.1" :exclusions [javax.servlet/servlet-api]]
                 [bk/ring-gzip "0.2.1"]
                 [compojure "1.6.0"]
                 [hiccup "1.0.5"]]

  :plugins [[codox "0.10.3"]
            [lein-ns-dep-graph "0.1.0-SNAPSHOT"]]

  :main video-server.main
  :aot [video-server.main]
  :uberjar-name "video-server.jar"

  :jvm-opts ["-Djava.awt.headless=true"]
  :repl-options {:init-ns user}

  :profiles {:dev {:source-paths ["dev"]}
             :uberjar {:aot :all}}

  :eastwood {:continue-on-exception true}
  :codox {:exclude user
          :output-dir "docs"
          :defaults {:doc/format :markdown}
          :src-dir-uri "http://github.com/hudren/video-server/blob/master/"
          :src-linenum-anchor-prefix "L"}

  :aliases {"build" ["uberjar"]
            "docs" ["do" "doc" ["shell" "open" "docs/index.html"]]
            "graph" ["with-profiles" "prod" "ns-dep-graph"]})

