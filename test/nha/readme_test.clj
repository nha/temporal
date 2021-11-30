(ns nha.readme-test
  (:require [nha.temporal :as t])
  (:import [java.time Duration]))

;; from the README.
;; usage: uncomment, REPL run
;;
;; (def task-queue "HelloActivityTaskQueue")
;; (def workflow-id "HelloActivityWorkflow")

;; (t/def-workflow-interface
;;   GreetingWorkflow
;;   (^String getGreeting [param-not-this] "say something yo"))

;; (t/def-activity-interface
;;   GreetingActivities
;;   (composeGreeting [^String greeting ^String n]))

;; (def my-workflow
;;   (reify GreetingWorkflow
;;     (getGreeting [this s]
;;       (let [actv (t/activity-stub GreetingActivities
;;                                   (t/activity-opts {:start-to-close-timeout (Duration/ofSeconds 2)}))]
;;         (.composeGreeting actv "HELLO " s)))))

;; (def my-activity (reify GreetingActivities
;;                    (composeGreeting [this greetings n]
;;                      (str greetings n))))

;; (let [{:keys [client] :as component} (-> (t/component task-queue)
;;                                          (t/start-component [(class my-workflow)]
;;                                                             [my-activity]))]
;;   (let [^GreetingWorkflow workflow (t/network-stub client
;;                                                    GreetingWorkflow
;;                                                    (t/workflow-options task-queue workflow-id))]

;;     (println (.getGreeting workflow "WORLD")) ;; <= will print "HELLO WORLD"
;;     )
;;   (t/stop-component component))
