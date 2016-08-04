(defproject com.hudren.homevideo/video-server "0.5.5-SNAPSHOT"
  :description "Videos@Home server"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.385"]
                 [org.clojure/core.cache "0.6.5"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/data.zip "0.1.2"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/tools.logging "0.3.1"]
                 [ch.qos.logback/logback-classic "1.1.7"]
                 [trptcolin/versioneer "0.2.0"]
                 [net.dongliu/apk-parser "2.1.4"]
                 [clj-http "3.1.0"]
                 [org.eclipse.jetty/jetty-server "9.2.16.v20160414"]
                 [org.eclipse.jetty/jetty-servlet "9.2.16.v20160414"]
                 [org.eclipse.jetty/jetty-servlets "9.2.16.v20160414"]
                 [javax.servlet/javax.servlet-api "3.1.0"]
                 [ring/ring-core "1.5.0"]
                 [ring/ring-devel "1.5.0"]
                 [ring/ring-servlet "1.5.0" :exclusions [javax.servlet/servlet-api]]
                 [ring/ring-defaults "0.2.1" :exclusions [javax.servlet/servlet-api]]
                 [bk/ring-gzip "0.1.1"]
                 [compojure "1.5.1"]
                 [hiccup "1.0.5"]]

  :plugins [[codox "0.8.10"]
            [lein-ns-dep-graph "0.1.0-SNAPSHOT"]]

  :main video-server.main
  :aot [video-server.main]
  :uberjar-name "video-server.jar"

  :repl-options {:init-ns user}

  :profiles {:dev {:source-paths ["dev"]}
             :prod {:jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :uberjar {:aot :all}}

  :eastwood {:continue-on-exception true}
  :codox {:exclude user
          :output-dir "docs"
          :defaults {:doc/format :markdown}
          :src-dir-uri "http://github.com/hudren/video-server/blob/master/"
          :src-linenum-anchor-prefix "L"}

  :aliases {"build" ["with-profiles" "prod" "do" "uberjar"]
            "docs" ["do" "doc" ["shell" "open" "docs/index.html"]]
            "graph" ["with-profiles" "prod" "ns-dep-graph"]})

