(defproject stateful-testing "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-beta1"]
                 [org.clojure/spec.alpha "0.1.123"]
                 ;[org.clojure/test.check "0.10.0-alpha2"]
                 [states "0.1.0"]
                 [compojure "1.6.0"]
                 [hiccup "1.0.5"]
                 [ring/ring-core "1.6.2"]
                 [ring/ring-jetty-adapter "1.6.2"]
                 [javax.servlet/javax.servlet-api "4.0.0"]
                 [kerodon "0.9.0"]
                 [peridot "0.5.0" :exclusions [commons-codec]]])
