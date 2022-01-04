(ns nha.temporal-test
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

(ApplicationFailure/newFailure
 "simulated error"
 "test"
 (into-array Object ["some details"
                     123]))


(ApplicationFailure/newNonRetryableFailure
 "another err"
 ""
 (into-array Object ["some details"
                     123]))

(ApplicationFailure/newNonRetryableFailureWithCause
 ""
 ""
 nil
 nil)

(ApplicationFailure/newNonRetryableFailureWithCause
 ""
 ""
 nil
 nil)

;; hardcode the
;; https://github.com/temporalio/samples-java/blob/main/src/main/java/io/temporal/samples/hello/HelloActivity.java
;; to demonstrate that use from Clojure without this library is possible
;; Left all the exploration steps in there too
;;
;; https://github.com/temporalio/samples-java/blob/d92d48ca4431d6ea6af564a2d303fad9a9d3521b/src/main/java/io/temporal/samples/hello/HelloActivity.java
;;

(def task-queue "HelloActivityTaskQueue")
(def workflow-id "HelloActivityWorkflow")

;; step 1: Workflow (the all-encompassing Thing)
;; https://github.com/temporalio/samples-java/blob/d92d48ca4431d6ea6af564a2d303fad9a9d3521b/src/main/java/io/temporal/samples/hello/HelloActivity.java#L54-L63

#_(definterface
      ^{Retention         RetentionPolicy/RUNTIME
        WorkflowInterface true}
      GreetingWorkflow

    ;; on method
    (^{Retention      RetentionPolicy/RUNTIME
       WorkflowMethod true}
     ^String getGreeting [param-not-this] "say something yo"))

(sut/def-workflow-interface
  ;; ^{java.lang.annotation.Retention RetentionPolicy/RUNTIME
  ;;   io.temporal.workflow.WorkflowInterface true}
  ;; no docstring? todo?
  ;; "define a workflow interface"
  GreetingWorkflow
  (;; ^{Retention RetentionPolicy/RUNTIME
   ;;   WorkflowMethod true}
   ^String getGreeting [param-not-this] "say something yo"))

(deftest GreetingWorkflow-test
  (testing "in the impl"

    (is (seq (.getAnnotations GreetingWorkflow)))

    (is (seq (.getAnnotations (.getMethod GreetingWorkflow "getGreeting"
                                          (into-array [Object])))))

    (is (some (partial instance? WorkflowMethod)
              (seq (.getAnnotations (.getMethod GreetingWorkflow "getGreeting"
                                                (into-array [Object]))))))))


;; step 2: activity (ie. some sort of side effect, but deterministic - otherwise temporal calls them side effects)
;; https://github.com/temporalio/samples-java/blob/d92d48ca4431d6ea6af564a2d303fad9a9d3521b/src/main/java/io/temporal/samples/hello/HelloActivity.java#L76-L81

(sut/def-activity-interface
  GreetingActivities
  (
   ;; not 100% equivalent to annotation, see notes below. However tests pass
   ;; ^{
   ;;   Retention RetentionPolicy/RUNTIME
   ;;   ;; small difference here, true instead of the below value - however tests
   ;;   ;; pass and this can be more concise
   ;;   ActivityMethod {:name "greet" :type "String"}
   ;;   }
   ;; ^String
   composeGreeting [^String greeting ^String n]))

(deftest GreetingActivities-test

  (is (seq (.getAnnotations GreetingActivities)))
  (is (some (partial instance? ActivityInterface)
            (seq (.getAnnotations GreetingActivities))))
  ;; not sure if I can get this
  #_(is (some (partial instance? ActivityMethod)
              (seq (.getAnnotations (.getMethod GreetingActivities "composeGreeting"
                                                (into-array [Object Object])) ))))
  )

;; step 3: workflow impl that uses the activity via a stub
;; skip to the last attempt from annotations straight away
;; https://github.com/temporalio/samples-java/blob/778337d39e2f5c463d22f7ace3034f6a0f663036/src/main/java/io/temporal/samples/hello/HelloActivity.java#L84
;;
;; I just commented out the annotations here.. turns out these are duly ignored
;;
(deftype
    ;;^{Retention RetentionPolicy/RUNTIME}
    GreetingWorkflowImplStep4
    []
  GreetingWorkflow
  (;; turns out these are duly ignored by deftype?
   ;; ^{Retention      RetentionPolicy/RUNTIME
   ;;   WorkflowMethod true}
   ;; ^String
   getGreeting [this s]

   (try
     (let [actv (Workflow/newActivityStub
                 GreetingActivities
                 (-> (ActivityOptions/newBuilder)
                     (.setStartToCloseTimeout (Duration/ofSeconds 2))
                     (.setRetryOptions (-> (RetryOptions/newBuilder)
                                           (.setBackoffCoefficient 2)
                                           (.setMaximumAttempts 2)
                                           ;; https://www.javadoc.io/doc/io.temporal/temporal-sdk/latest/io/temporal/common/RetryOptions.Builder.html
                                           ;; TODO understand this.
                                           ;; Problly easier to wrap
                                           ;; try/catch everywhere and
                                           ;; return these
                                           ;; https://www.javadoc.io/doc/io.temporal/temporal-sdk/latest/io/temporal/failure/ApplicationFailure.html
                                           ;; from clj
                                           (.setDoNotRetry (into-array String []))
                                           (.build)))
                     (.build)))]
       (println "GOT ACTIVITY ") ;; NOTE: cannot print actv
       (.composeGreeting actv "HELLO " s))
     (catch Exception ex
       (println "ERROR - " ex)

       ;; could also do this
       ;; (throw (Activity/wrap ex))

       "KO"))))

;; because the annotations are ignored/optional..
;; -> prefer reify?
(def reified-workflow
  (reify GreetingWorkflow
    (getGreeting [this s]
      (try
        (let [actv
              (sut/activity-stub
               GreetingActivities
               (sut/activity-opts {:start-to-close-timeout (Duration/ofSeconds 2)
                                   :retry-options          (sut/retry-options {:backoff-coefficient 2
                                                                               :maximum-attempts    2
                                                                               :do-not-retry-coll   ["noretry"]})}))]
          (println "GOT ACTIVITY ") ;; NOTE: cannot print actv
          (.composeGreeting actv "HELLO " s))
        (catch Exception ex
          (println "ERROR - " ex)

          ;; could also do this
          ;; (throw (Activity/wrap ex))

          "KO")))))

;; or even defrecord
(defrecord MyWorkflowImpl []
  GreetingWorkflow
  (getGreeting [this s]
    (try
      (let [actv
            (sut/activity-stub
             GreetingActivities
             (sut/activity-opts {:start-to-close-timeout (Duration/ofSeconds 2)
                                 :retry-options          (sut/retry-options {:backoff-coefficient 2
                                                                             :maximum-attempts    2
                                                                             :do-not-retry-coll   ["noretry"]})}))]
        (println "GOT ACTIVITY ") ;; NOTE: cannot print actv
        (.composeGreeting actv "HELLO " s))
      (catch Exception ex
        (println "ERROR - " ex)

        ;; could also do this
        ;; (throw (Activity/wrap ex))

        "KO"))))


;; https://github.com/temporalio/samples-java/blob/d92d48ca4431d6ea6af564a2d303fad9a9d3521b/src/main/java/io/temporal/samples/hello/HelloActivity.java#L109-L114


(def my-activity (reify GreetingActivities
                   (composeGreeting [this greetings n]
                     (str greetings n))))

(defn in-ci? []
  (= "true" (System/getenv "CI")))

(def test-worker-opts (sut/worker-opts
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
(def test-factory-opts (sut/worker-factory-opts
                        {:enable-logging-in-replay                                 false
                         :max-workflow-thread-count                                2
                         :workflow-cache-size                                      2
                         :workflow-host-local-poll-thread-count                    2
                         :workflow-host-local-task-queue-schedule-to-start-timeout (Duration/ofSeconds 2)
                         }))

(deftest run-workflow
  (println "in CI? " (in-ci?))

  (let [ci?  (in-ci?)
        opts {:factory-opts test-factory-opts
              :worker-opts  test-worker-opts
              :service-opts (sut/workflow-service-stubs-opts {:target "127.0.0.1:7233"})}

        {:keys [test-env service client factory worker] :as component}
        (if ci?
          (-> (testsut/test-component task-queue
                                      (assoc opts :test-env-opts (testsut/test-env-opts opts)))
              (testsut/start-component [(class reified-workflow)]
                                       [my-activity]))
          (-> (sut/component task-queue opts)
              (sut/start-component [;; either of these work
                                    ;; GreetingWorkflowImplStep4
                                    ;;(class reified-workflow)
                                    MyWorkflowImpl]
                                   [my-activity])))]

    (println "COMPONENT STARTED ")

    (let [^GreetingWorkflow workflow (sut/network-stub client
                                                       GreetingWorkflow
                                                       (sut/workflow-options task-queue workflow-id))]

      (is (= "HELLO WORLD" (.getGreeting workflow "WORLD")))
      (println "TEST DONE"))

    (if ci?
      (testsut/stop-component component)
      (sut/stop-component component))))
