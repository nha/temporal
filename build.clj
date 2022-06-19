(ns build
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as bb]))

(def lib 'org.clojars.nha/temporal)
;; temporal SDK version first
(def version (format "1.12.0.%s" (b/git-count-revs nil)))

(defn bb-opts [opts]
  (assoc opts
         :lib lib
         :version version))

(defn eastwood "Run Eastwood." [opts]
  (-> opts (bb/run-task [:eastwood])))

(defn ci "Run the CI pipeline of tests (and build the JAR)." [opts]
  (-> opts
      (assoc :lib lib :version version)
      ;; (bb/run-task [:eastwood])
      (bb/run-tests)
      (bb/clean)
      (bb/jar)))

(defn clean [opts]
  (-> opts
     (bb/clean)))

(defn install "Install the JAR locally." [opts]
  (-> opts
      (assoc :lib lib :version version)
      (bb/install)))

(defn deploy
  "Deploy the JAR to Clojars.
  needs CLOJARS_USERNAME and CLOJARS_PASSWORD
  as well as the jar"
  [opts]
  (let [o (bb-opts opts)]
    (assert (System/getenv "CLOJARS_USERNAME"))
    (assert (System/getenv "CLOJARS_PASSWORD"))
    (println "Deploying version " o)
    (bb/deploy o)))
