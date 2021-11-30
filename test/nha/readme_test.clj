(ns nha.readme-test
  (:require [clojure.test :refer [deftest is testing]]
            [nha.temporal :as t]))

(comment
  ;; this talks to a real service that has to be installed locally
  ;; so comment for now

  (deftest readme-test
    (testing "example from the README works "

      (def task-queue "HelloActivityTaskQueue")
      (def workflow-id "HelloActivityWorkflow")

      (t/def-workflow-interface
        GreetingWorkflow
        (^String getGreeting [param-not-this] "say something yo"))

      (t/def-activity-interface
        GreetingActivities
        (composeGreeting [^String greeting ^String n]))

      (def my-workflow
        (reify GreetingWorkflow
          (getGreeting [this s]
            (let [a (t/activity-stub GreetingActivities)]
              (.composeGreeting a "HELLO " s)))))

      (def my-activity (reify GreetingActivities
                         (composeGreeting [this greetings n]
                           (str greetings n))))

      (let [{:keys [client] :as component} (t/component task-queue
                                                        [(class my-workflow)]
                                                        [my-activity])]
        (let [^GreetingWorkflow workflow (t/network-stub client
                                                         GreetingWorkflow
                                                         (t/workflow-options task-queue workflow-id))]
          (println (.getGreeting workflow "WORLD")) ;; will print "HELLO WORLD"
          )

        (t/stop-component component)))
    ))
