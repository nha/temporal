(ns nha.annotation-test
  (:require
   [clojure.test :refer [deftest testing is are]])
  (:import
   [io.temporal.activity Activity ActivityInterface ActivityMethod ActivityOptions]
   [io.temporal.client WorkflowClient WorkflowOptions WorkflowClientOptions]
   [io.temporal.serviceclient WorkflowServiceStubs]
   [io.temporal.testing TestEnvironmentOptions TestWorkflowEnvironment]
   [io.temporal.worker Worker WorkerFactory WorkerOptions WorkerFactoryOptions]
   [io.temporal.workflow Workflow WorkflowInterface WorkflowMethod]
   [io.temporal.serviceclient WorkflowServiceStubsOptions]
   [java.lang.annotation Retention RetentionPolicy Target ElementType]
   [io.temporal.common RetryOptions]
   [io.temporal.failure ApplicationFailure]
   [java.time Duration]
   [java.util.concurrent TimeUnit]))

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

(definterface ^{Retention RetentionPolicy/RUNTIME
                WorkflowInterface true}
    GreetingWorkflow

  ;; on method
  (^{Retention RetentionPolicy/RUNTIME
     WorkflowMethod true}
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

(definterface ^{Retention RetentionPolicy/RUNTIME
                ActivityInterface true
                ;; namePrefix = ;; ? multi-something perhaps?
                }
    GreetingActivities

  ;; on method
  (^{Retention RetentionPolicy/RUNTIME
     ActivityMethod {:name "greet" :type "String"}
     ;; might be different syntax maybe
     ;; ActivityMethod (ActivityMethod {:name "greet" :type "String"})
     ;; ActivityMethod [(ActivityMethod {:name "greet" :type "String"})]
     ;; ActivityMethod [{:name "greet" :type "String"}]
     ;; ...
     }
   ^String composeGreeting [^String greeting ^String n]))


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
;; attempt 1
;; https://github.com/temporalio/samples-java/blob/778337d39e2f5c463d22f7ace3034f6a0f663036/src/main/java/io/temporal/samples/hello/HelloActivity.java#L84

(deftype
    ^{Retention RetentionPolicy/RUNTIME}
    GreetingWorkflowImplStep1
    []
  :load-ns false
  GreetingWorkflow
  (^{Retention      RetentionPolicy/RUNTIME
     WorkflowMethod true}
   ^String
   getGreeting [this s] (str "HELLO " s)))

(deftest GreetingWorkflowImplStep1-test

  (testing "is usable"
    (is (= "HELLO NICO "
           (.getGreeting (GreetingWorkflowImplStep1.)
                      "NICO "))))

  (testing "in the impl"

    (is (seq (.getAnnotations GreetingWorkflowImplStep1)))

    (is (seq (.getAnnotations (.getMethod GreetingWorkflowImplStep1 "getGreeting"
                                          (into-array [Object])))))

    (is (some (partial instance? WorkflowMethod)
              (seq (.getAnnotations (.getMethod GreetingWorkflowImplStep1 "getGreeting"
                                                (into-array [Object])))))))
  )

(comment
  ;; however this doesn't work: NEEDS to be called inside a WorkflowImpl
  ;; again not very clojure-y but I suspect they cannot work the "retry at a given
  ;; line" sort of magic otherwise
  (def activities-stub (Workflow/newActivityStub
                         GreetingActivities
                         (-> (ActivityOptions/newBuilder)
                             (.setStartToCloseTimeout (Duration/ofSeconds 2))
                             (.build))))
  ;; so we end up with the below trick instead
  )

;; step 3: workflow impl that uses the activity via a stub
;; attempt 2
;; still https://github.com/temporalio/samples-java/blob/778337d39e2f5c463d22f7ace3034f6a0f663036/src/main/java/io/temporal/samples/hello/HelloActivity.java#L84


(definterface TemporalWorkflow (init [] "poor man's constructor"))

(deftype
    ^{Retention RetentionPolicy/RUNTIME}
    GreetingWorkflowImplStep2
    [^:unsynchronized-mutable activities-params
     ^:unsynchronized-mutable activities-stub]

  TemporalWorkflow
  (init [this]
    ;; KO EVEN HERE - THIS HAS TO BE CALLED IN THE WORKFLOW - FOR REAL
    ;; => NEED GEN-CLASS
    ;; => THEN NEED A GOOD WAY TO REPL
    (set! activities-stub (Workflow/newActivityStub
                            GreetingActivities
                            (-> (ActivityOptions/newBuilder)
                                (.setStartToCloseTimeout (Duration/ofSeconds 2))
                                (.build))))
    nil)

  GreetingWorkflow
  (^{Retention RetentionPolicy/RUNTIME
     WorkflowMethod true}
   ^String
   getGreeting [this s]
   (str "HELLO " s)))


(deftest GreetingWorkflowImplStep2-test

  (is (seq (.getAnnotations GreetingWorkflowImplStep2)))

  (is (seq (.getAnnotations (.getMethod GreetingWorkflowImplStep2 "getGreeting"
                                        (into-array [Object])))))

  (is (some (partial instance? WorkflowMethod)
            (seq (.getAnnotations (.getMethod GreetingWorkflowImplStep2 "getGreeting"
                                              (into-array [Object]))))))

  (is (= "HELLO NICO "
         (.getGreeting (GreetingWorkflowImplStep2. nil nil) "NICO ")))

  (is (true?
        (try
          (= "HELLO NICO "
             (.getGreeting (-> (GreetingWorkflowImplStep2. nil nil)
                            (.init)
                            ;; need to bind init to the workflow thread
                            ;; which is only true in a "real" workfow
                            ;;
                            )
                        "NICO "))
          false
          (catch Exception ex false)
          (catch Throwable t
            ;; just to show that the Error fails
            true)))))


;; => no choice but to gen-class it seems

;; step 3: workflow impl that uses the activity via a stub
;; attempt 3
;; still https://github.com/temporalio/samples-java/blob/778337d39e2f5c463d22f7ace3034f6a0f663036/src/main/java/io/temporal/samples/hello/HelloActivity.java#L84

;;
;; NOTE: can I use proxy to mane anonymous workflows? would it be supported by Temporal?
;; if so then same syntactic definition as fn / defn = def fn would be cool
;;

(defn tw-init []
  (println "INIT")
  [[] (atom {:activities-stub (Workflow/newActivityStub
                               GreetingActivities
                               (-> (ActivityOptions/newBuilder)
                                   (.setStartToCloseTimeout (Duration/ofSeconds 2))
                                   (.build)))})])

(defn tw-post-init []
  (println "POST INIT "))

;; would work.. but NOT in the REPL
(gen-class
 :name "nha.annotation_test.GreetingWorkflowImplStep3"
 :implements [GreetingWorkflow]
 :state "state"
 :init "init"
 :post-init "post-init"
 :constructors {[] []}
 :prefix "tw-"
 :load-impl-ns false
 :main false)

(comment

  (try
    (nha.annotation_test.GreetingWorkflowImplStep2. nil nil)

    (nha.annotation_test.GreetingWorkflowImplStep3. nil nil) ;; not seen in the REPL straight away
    ;; even with bindings
    ;; needs a Clojure (compile ) step
    ;; https://www.reddit.com/r/Clojure/comments/5xwqed/genclass_repl_usage_question/

    (catch Exception ex
      ex))

  )
;; nha.annotation_test.GreetingWorkflowImplStep3

;; step 3: workflow impl that uses the activity via a stub
;; attempt 4
;; still https://github.com/temporalio/samples-java/blob/778337d39e2f5c463d22f7ace3034f6a0f663036/src/main/java/io/temporal/samples/hello/HelloActivity.java#L84
;; try reify and proxy.. after all Temporal just needs a Class right?
;; but then still issue of local state.. delay the creation of activities stub?
;; OR back to deftype


#_(def MyProxy (proxy [GreetingWorkflow] []
               (getGreeting [param-not-this] "say something yo")))

(let [activities (delay (Workflow/newActivityStub
                         GreetingActivities
                         (-> (ActivityOptions/newBuilder)
                             (.setStartToCloseTimeout (Duration/ofSeconds 2))
                             (.build))))]
  (deftype
      ^{Retention RetentionPolicy/RUNTIME}
      GreetingWorkflowImplStep4
      []
    GreetingWorkflow
    (^{Retention      RetentionPolicy/RUNTIME
       WorkflowMethod true}
     ^String
     getGreeting [this s]
     ;; call activity here instead
     ;; https://github.com/temporalio/samples-java/blob/778337d39e2f5c463d22f7ace3034f6a0f663036/src/main/java/io/temporal/samples/hello/HelloActivity.java#L104
     (str "HELLO " s)
     ;; (.composeGreeting @activities s) ;; NOOO deftype cannot see closures !
     (println "will make activities")
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

         "KO")))))


;; https://github.com/temporalio/samples-java/blob/d92d48ca4431d6ea6af564a2d303fad9a9d3521b/src/main/java/io/temporal/samples/hello/HelloActivity.java#L109-L114

;; TODO try bind compile-path "."

(def my-activity (reify GreetingActivities
                   (composeGreeting [this greetings n]
                     (str greetings n))))

(defn in-ci? []
  (= "true" (System/getenv "CI")))



;; https://www.javadoc.io/static/io.temporal/temporal-sdk/1.5.0/io/temporal/worker/WorkerOptions.Builder.html
(def test-worker-opts (-> (WorkerOptions/newBuilder)
                          (.setMaxConcurrentActivityExecutionSize 2)
                          (.setMaxConcurrentWorkflowTaskExecutionSize 2)
                          (.setMaxConcurrentLocalActivityExecutionSize 2)
                          (.setActivityPollThreadCount 2)
                          ;;(.setDefaultDeadlockDetectionTimeout 1000)
                          ;;(.setLocalActivityWorkerOnly true) ;; for tests
                          (.setMaxConcurrentActivityExecutionSize 2)
                          (.setMaxTaskQueueActivitiesPerSecond 2)
                          (.setMaxWorkerActivitiesPerSecond 2)
                          (.build)))

;; https://www.javadoc.io/static/io.temporal/temporal-sdk/1.5.0/io/temporal/worker/WorkerFactoryOptions.Builder.html
(def test-factory-opts (-> (WorkerFactoryOptions/newBuilder)
                           ;; (.setActivityInterceptors (new MDCActivityInterceptor()))
                           (.setEnableLoggingInReplay false)
                           (.setMaxWorkflowThreadCount 2)
                           (.setWorkflowCacheSize 2)
                           (.setWorkflowHostLocalPollThreadCount 2)
                           ;;(.setWorkflowHostLocalTaskQueueScheduleToStartTimeout Duration/...)
                           (.build)))

(deftest run-workflow
  (println "in CI? " (in-ci?))

  (let [^TestWorkflowEnvironment test-environment (when (in-ci?)
                                                    (TestWorkflowEnvironment/newInstance (->(TestEnvironmentOptions/newBuilder)
                                                                                            (.setWorkerFactoryOptions test-factory-opts)
                                                                                            ;;(.setWorkflowClientOptions test-worker-opts)
                                                                                            (.build))))
        ^WorkflowServiceStubs service                  (if (in-ci?)
                                                         nil
                                                         (WorkflowServiceStubs/newInstance
                                                           (-> (WorkflowServiceStubsOptions/newBuilder)
                                                               (.setTarget "127.0.0.1:7233") ;; default local docker
                                                               (.build))))
        ^WorkflowClient client                    (if (in-ci?)
                                                    (.getWorkflowClient test-environment)
                                                    (WorkflowClient/newInstance service
                                                                                (-> (WorkflowClientOptions/newBuilder)
                                                                                    (.build))))
        ^WorkerFactory factory                    (if (in-ci?)
                                                    nil
                                                    (WorkerFactory/newInstance client test-factory-opts))


        ^Worker worker (if (in-ci?)
                         (.newWorker test-environment task-queue test-worker-opts)
                         (.newWorker factory task-queue test-worker-opts))]

    (def component {:service service
                    :client  client
                    :factory factory
                    :worker  worker})

    (println "WILL REGISTER " component)
    (.registerWorkflowImplementationTypes worker (into-array [GreetingWorkflowImplStep4]))
    (println "DID REGISTER ")
    (.registerActivitiesImplementations worker  (into-array [my-activity]))

    (println "WILL START FACTORY ")
    (if (in-ci?)
      (.start test-environment)
      (.start factory))

    (println "STARTED FACTORY")
    (let [^GreetingWorkflow workflow (.newWorkflowStub client
                                                       GreetingWorkflow
                                                       (-> (WorkflowOptions/newBuilder)
                                                           (.setWorkflowId workflow-id)
                                                           (.setTaskQueue task-queue)
                                                           (.build)))]

      (is (= "HELLO WORLD" (.getGreeting workflow "WORLD")))
      ;; (is (= "HELLO NICO" (.getGreeting workflow "NICO")))

      (println "TEST DONE")

      ;; test-env
      ;; service
      ;; client
      ;; factory
      ;; worker

      (when-not (in-ci?)
        (.shutdown factory)
        (.awaitTermination factory 1 TimeUnit/SECONDS))

      ;; client here


      (if (in-ci?)
        (.close test-environment)
        (do
          (.shutdown service)
          (Thread/sleep 100)
          (.shutdownNow service)
          (.awaitTermination service 1 TimeUnit/SECONDS))))))
