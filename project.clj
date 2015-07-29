(defproject com.hudren.homevideo/video-server "0.5-SNAPSHOT"
  :description "Videos@Home server"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.clojure/core.cache "0.6.4"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/data.zip "0.1.1"]
                 [org.clojure/tools.cli "0.3.2"]
                 [org.clojure/tools.logging "0.3.1"]
                 [ch.qos.logback/logback-classic "1.1.3"]
                 [trptcolin/versioneer "0.2.0"]
                 [net.dongliu/apk-parser "2.0.15"]
                 [clj-http "2.0.0"]
                 [org.eclipse.jetty/jetty-server "9.2.10.v20150310"]
                 [org.eclipse.jetty/jetty-servlet "9.2.10.v20150310"]
                 [org.eclipse.jetty/jetty-servlets "9.2.10.v20150310"]
                 [javax.servlet/javax.servlet-api "3.1.0"]
                 [ring/ring-core "1.4.0"]
                 [ring/ring-devel "1.4.0"]
                 [ring/ring-servlet "1.4.0" :exclusions [javax.servlet/servlet-api]]
                 [ring/ring-defaults "0.1.5" :exclusions [javax.servlet/servlet-api]]
                 [bk/ring-gzip "0.1.1"]
                 [compojure "1.4.0"]
                 [enlive "1.1.6"]]

  :plugins [[lein-bower "0.5.1"]
            [codox "0.8.10"]
            [lein-ns-dep-graph "0.1.0-SNAPSHOT"]]

  :bower-dependencies [[polymer "~1.0.4"]
                       [paper-styles "PolymerElements/paper-styles#~1.0.7"]
                       [paper-header-panel "PolymerElements/paper-header-panel#~1.0.2"]
                       [paper-toolbar "PolymerElements/paper-toolbar#~1.0.2"]
                       [paper-button "PolymerElements/paper-button#~1.0.1"]
                       [paper-tabs "PolymerElements/paper-tabs#~1.0.0"]]

  :bower {:directory "resources/public/components"}

  :main video-server.main
  :aot [video-server.main]
  :uberjar-name "video-server.jar"

  :repl-options {:init-ns user}

  :profiles {:dev {:source-paths ["dev"]}
             :prod {}
             :uberjar {:aot :all}}

  :eastwood {:continue-on-exception true}
  :codox {:exclude user
          :output-dir "docs"
          :defaults {:doc/format :markdown}
          :src-dir-uri "http://github.com/hudren/video-server/blob/master/"
          :src-linenum-anchor-prefix "L"}

  :aliases {"build" ["with-profiles" "prod" "do" "clean" ["bower" "install"] "uberjar"]
            "docs" ["do" "doc" ["shell" "open" "docs/index.html"]]
            "graph" ["with-profiles" "prod" "ns-dep-graph"]})

