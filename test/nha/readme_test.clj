(ns nha.readme-test
  (:require [clojure.test :refer [deftest is]]
            [nha.temporal :as t]))

(deftest readme-test

  (def task-queue "HelloActivityTaskQueue")
  (def workflow-id "HelloActivityWorkflow")

  (t/def-workflow-interface
    GreetingWorkflow
    (^String getGreeting [param-not-this] "say something yo"))
  
  (t/def-activity-interface
    GreetingActivities
    (composeGreeting [^String greeting ^String n]))

  (def reified-workflow
    (reify GreetingWorkflow
      (getGreeting [this s]
        (let [a (t/activity-stub GreetingActivities)]
          (.composeGreeting a "HELLO " s)))))
  
  (let [{:keys [test-env service client factory worker] :as component}
        (t/component task-queue
                     [(class reified-workflow)]
                     [my-activity])]
    (let [^GreetingWorkflow workflow (t/network-stub client
                                                     GreetingWorkflow
                                                     (t/workflow-options task-queue workflow-id))]


      (println (.getGreeting workflow "WORLD")) ;; will print "HELLO WORLD"
      )

    (t/stop-component component))
  )
