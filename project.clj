(defproject com.hudren.homevideo/video-server "0.3.4"
  :description "Videos@Home server"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.clojure/core.cache "0.6.4"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.cli "0.3.1"]
                 [org.clojure/tools.logging "0.3.1"]
                 [ch.qos.logback/logback-classic "1.1.3"]
                 [trptcolin/versioneer "0.2.0"]
                 [net.dongliu/apk-parser "2.0.12"]
                 [clj-http "1.1.2"]
                 [org.eclipse.jetty/jetty-server "9.2.10.v20150310"]
                 [org.eclipse.jetty/jetty-servlet "9.2.10.v20150310"]
                 [org.eclipse.jetty/jetty-servlets "9.2.10.v20150310"]
                 [javax.servlet/javax.servlet-api "3.1.0"]
                 [ring/ring-core "1.3.2"]
                 [ring/ring-defaults "0.1.5" :exclusions [javax.servlet/servlet-api]]
                 [ring/ring-devel "1.3.2"]
                 [ring/ring-servlet "1.3.2" :exclusions [javax.servlet/servlet-api]]
                 [bk/ring-gzip "0.1.1"]
                 [compojure "1.3.4"]
                 [enlive "1.1.5"]]

  :plugins [[lein-bower "0.5.1"]
            [codox "0.8.10"]
            [lein-ns-dep-graph "0.1.0-SNAPSHOT"]]

  :bower-dependencies [[polymer "~0.5.1"]
                       [font-roboto "Polymer/font-roboto#~0.5.1"]
                       [core-header-panel "Polymer/core-header-panel#~0.5.1"]
                       [core-toolbar "Polymer/core-toolbar#~0.5.1"]
                       [paper-button "Polymer/paper-button#~0.5.1"]
                       [paper-tabs "Polymer/paper-tabs#~0.5.1"]]

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

