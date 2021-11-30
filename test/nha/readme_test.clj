(ns nha.readme-test
  (:require [clojure.test :refer [deftest is testing]]
            [nha.temporal :as t])
  (:import [java.time Duration]))

;; this talks to a real service that has to be installed locally
;; so comment for now
;; NOTE: there can be only one instance of interface/activity interface etc.?
#_(ns nha.temporal-test
  (:require
   [clojure.test :refer [deftest testing is are]]
   [nha.temporal :as sut]
   [nha.temporal.testing :as testsut])
  (:import
   [io.temporal.activity Activity ActivityInterface ActivityMethod ActivityOptions]
   [io.temporal.client WorkflowClient WorkflowOptions WorkflowClientOptions]
   [io.temporal.common RetryOptions]
   [io.temporal.failure ApplicationFailure]
   [io.temporal.serviceclient WorkflowServiceStubs WorkflowServiceStubsOptions]
   [io.temporal.testing TestEnvironmentOptions TestWorkflowEnvironment]
   [io.temporal.worker Worker WorkerFactory WorkerOptions WorkerFactoryOptions]
   [io.temporal.workflow Workflow WorkflowInterface WorkflowMethod]
   [java.lang.annotation Retention RetentionPolicy Target ElementType]
   [java.time Duration]
   [java.util.concurrent TimeUnit]))

;; same as the simple example. But uses the clj helpers defined

;; hardcode the
;; https://github.com/temporalio/samples-java/blob/main/src/main/java/io/temporal/samples/hello/HelloActivity.java
;; to demonstrate that use from Clojure without this library is possible
;; Left all the exploration steps in there too
;;
;; https://github.com/temporalio/samples-java/blob/d92d48ca4431d6ea6af564a2d303fad9a9d3521b/src/main/java/io/temporal/samples/hello/HelloActivity.java
;;

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
      (try
        (let [actv
              (t/activity-stub
               GreetingActivities
               (t/activity-opts {:start-to-close-timeout (Duration/ofSeconds 2)
                                   :retry-options          (t/retry-options {:backoff-coefficient 2
                                                                               :maximum-attempts    2
                                                                               :do-not-retry-coll   ["noretry"]})}))]
          (println "GOT ACTIVITY ") ;; NOTE: cannot print actv
          (.composeGreeting actv "HELLO " s))
        (catch Exception ex
          (println "ERROR - " ex)

          ;; could also do this
          ;; (throw (Activity/wrap ex))

          "KO")))))


;; https://github.com/temporalio/samples-java/blob/d92d48ca4431d6ea6af564a2d303fad9a9d3521b/src/main/java/io/temporal/samples/hello/HelloActivity.java#L109-L114


(def my-activity (reify GreetingActivities
                   (composeGreeting [this greetings n]
                     (str greetings n))))

(def test-worker-opts (t/worker-opts
                       {:max-concurrent-activity-execution-size       2
                        :max-concurrent-workflow-task-execution-size  2
                        :max-concurrent-local-activity-execution-size 2
                        :activity-poll-thread-count                   2
                        :default-deadlock-detection-timeout           1000
                        ;; :local-activity-worker-only? false
                        :max-task-queue-activities-per-second         2
                        :max-worker-activities-per-second             2
                        }))

;; https://www.javadoc.io/static/io.temporal/temporal-sdk/1.5.0/io/temporal/worker/WorkerFactoryOptions.Builder.html
(def test-factory-opts (t/worker-factory-opts
                        {:enable-logging-in-replay                                 false
                         :max-workflow-thread-count                                2
                         :workflow-cache-size                                      2
                         :workflow-host-local-poll-thread-count                    2
                         :workflow-host-local-task-queue-schedule-to-start-timeout (Duration/ofSeconds 2)
                         }))

(deftest run-workflow-readme
  

  (let [{:keys [test-env service client factory worker] :as component}
        
        (t/component task-queue
                     [;; either of these work
                      ;; GreetingWorkflowImplStep4
                      (class reified-workflow)]
                     [my-activity])]

    (println "COMPONENT STARTED ")

    (let [^GreetingWorkflow workflow (t/network-stub client
                                                       GreetingWorkflow
                                                       (t/workflow-options task-queue workflow-id))]

      (is (= "HELLO WORLD" (.getGreeting workflow "WORLD")))
      (println "TEST DONE"))

    (t/stop-component component)))
