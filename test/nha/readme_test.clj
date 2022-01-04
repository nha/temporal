(ns nha.readme-test
  (:require [nha.temporal :as t])
  (:import [java.time Duration]))

;; from the README.
;; usage: uncomment, REPL run
;;
(comment
  (def task-queue "HelloActivityTaskQueue")
  (def workflow-id "HelloActivityWorkflow")

  (t/def-workflow-interface
    GreetingWorkflow
    (^String getGreeting [param-not-this] "say something yo"))

  (t/def-activity-interface
    GreetingActivities
    (composeGreeting [^String greeting ^String n]))

  (defrecord MyWorkflowImpl []
    GreetingWorkflow
    (getGreeting [this s]
      (let [actv (t/activity-stub
                  GreetingActivities
                  (t/activity-opts {:start-to-close-timeout (Duration/ofSeconds 2)}))]
        (.composeGreeting actv "HELLO " s))))

  (defrecord MyActivityImpl []
    GreetingActivities
    (composeGreeting [this greetings n]
      (str greetings n)))

  (let [{:keys [client] :as component} (-> (t/component task-queue)
                                           (t/start-component [MyWorkflowImpl] [(MyActivityImpl.)]))]
    (let [^GreetingWorkflow workflow (t/network-stub client GreetingWorkflow (t/workflow-options task-queue workflow-id))]
      ;; this will print "HELLO WORLD"
      (println (.getGreeting workflow "WORLD")))
    (t/stop-component component)))
