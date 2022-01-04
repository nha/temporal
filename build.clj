(ns build
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as bb]))

(def lib 'org.clojars.nha/temporal)
;; temporal SDK version first
(def version (format "1.5.0.%s" (b/git-count-revs nil)))
;; (def class-dir "target/classes")
;; (def basis (b/create-basis {:project "deps.edn"}))
;;(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn bb-opts [opts]
  (assoc opts
         :lib lib
         :version version
         ;; :class-dir class-dir
         ;; :jar-file jar-file
         ))

(defn eastwood "Run Eastwood." [opts]
  (-> opts (bb/run-task [:eastwood])))

(defn ci "Run the CI pipeline of tests (and build the JAR)." [opts]
  (-> opts
      (assoc :lib lib :version version)
      ;; (bb/run-task [:eastwood])
      ;;(bb/run-tests)
      (bb/clean)
      (bb/jar)))

(defn clean [opts]
  (-> opts
     (bb/clean)))

(defn install "Install the JAR locally." [opts]
  (-> opts
      (assoc :lib lib :version version)
      (bb/install)))

#_(defn jar [opts]
  (bb/clean (bb-opts opts))
  ;; (b/write-pom {:class-dir class-dir
  ;;               :lib       lib
  ;;               :version   version
  ;;               :basis     basis
  ;;               :src-dirs  ["src"]})
  ;; (b/copy-dir {:src-dirs   ["src" "resources"]
  ;;              :target-dir class-dir})
  ;; (b/jar {:class-dir class-dir
  ;;         :jar-file  jar-file})
  )


(defn deploy
  "Deploy the JAR to Clojars.
  needs CLOJARS_USERNAME and CLOJARS_PASSWORD
  as well as the jar"
  [opts]
  (-> opts
      (bb-opts)
      (bb/deploy)))
