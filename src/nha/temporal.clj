(ns nha.temporal
  (:import
   [io.temporal.activity ActivityInterface ActivityMethod ActivityOptions]
   [io.temporal.client WorkflowClient WorkflowOptions WorkflowClientOptions]
   [io.temporal.common RetryOptions]
   [io.temporal.serviceclient WorkflowServiceStubs WorkflowServiceStubsOptions]
   [io.temporal.worker Worker WorkerFactory WorkerOptions WorkerFactoryOptions]
   [io.temporal.workflow Workflow WorkflowInterface WorkflowMethod]
   [java.lang.annotation Retention RetentionPolicy Target ElementType]
   [java.time Duration]
   [java.util.concurrent TimeUnit]))

;; macros to reduce annotations

(defmacro def-workflow-interface
  ;; modified version of definterface that adds some metadata by default
  [name & sigs]
  ;; TODO see if imports necessary (and where)
  (import 'java.lang.annotation.Retention)
  (import 'io.temporal.workflow.WorkflowMethod)
  (let [tag (fn [x] (or (:tag (meta x)) Object))
        psig (fn [[name [& args]]]
               (vector
                 ;; change: add meta - OK
                 (with-meta name
                   (merge
                     '{java.lang.annotation.Retention RetentionPolicy/RUNTIME
                      io.temporal.workflow.WorkflowMethod true}
                     (meta name)))
                 (vec (map tag args)) (tag name) (map meta args)))
        cname (with-meta (symbol (str (namespace-munge *ns*) "." name))
                ;; change - add meta by default - OK
                (merge
                  '{java.lang.annotation.Retention RetentionPolicy/RUNTIME
                    io.temporal.workflow.WorkflowInterface true}
                  (meta name)))]
    ;;(println "interface name " (into {} (seq (meta cname))))
    `(let []
       (gen-interface :name ~cname :methods ~(vec (map psig sigs)))
       (import ~cname))))

(comment
  (def-workflow-interface
    ;; no docstring? todo?
    ;; "define a workflow interface"
    GreetingWorkflow
    (^String getGreeting [param-not-this] "say something yo"))
  )


(defmacro def-activity-interface
  ;; modified version of definterface that adds some metadata by default
  [name & sigs]
  ;; TODO see if imports necessary (and where)
  (import 'java.lang.annotation.Retention)
  (import 'io.temporal.activity.ActivityInterface)
  (let [tag (fn [x] (or (:tag (meta x)) Object))
        psig (fn [[name [& args]]]
               (vector
                 ;; change: add meta
                 (with-meta name
                   (merge
                     `~{java.lang.annotation.Retention RetentionPolicy/RUNTIME
                        io.temporal.activity.ActivityMethod true ;; let it be
                        ;; overriden if necessary
                        }
                     (meta name)))
                 (vec (map tag args)) (tag name) (map meta args)))
        cname (with-meta (symbol (str (namespace-munge *ns*) "." name))
                ;; change - add meta by default
                (merge
                  '{java.lang.annotation.Retention RetentionPolicy/RUNTIME
                    io.temporal.activity.ActivityInterface true}
                  (meta name)))]
    ;; (println "interface name " (into {} (seq (meta cname))))
    `(let []
       (gen-interface :name ~cname :methods ~(vec (map psig sigs)))
       (import ~cname))))

(comment
  (def-activity-interface
    GreetingActivities
    (^{Retention RetentionPolicy/RUNTIME
       ActivityMethod {:name "greet" :type "String"}}
     ^String composeGreeting [^String greeting ^String n]))
  )

;; useless - no annotations needed here. Just deftype/reify work
#_(defmacro def-workflow-impl)

;; light Clojure helpers

(defn ^RetryOptions retry-options
  "Helper to build a (RetryOptions)[https://www.javadoc.io/doc/io.temporal/temporal-sdk/latest/io/temporal/common/RetryOptions.Builder.html]
  example usage:
  (activity-opts
    {:backoff-coefficient 2
     :maximum-attempts 2
     :do-not-retry-coll [\"test\"]
     })
  all entries are optional
  "
  [{:keys [backoff-coefficient maximum-attempts do-not-retry-coll]}]
  (.build
    (cond-> (RetryOptions/newBuilder)
      backoff-coefficient (.setBackoffCoefficient backoff-coefficient)
      maximum-attempts (.setMaximumAttempts maximum-attempts)
      ;; https://www.javadoc.io/doc/io.temporal/temporal-sdk/latest/io/temporal/common/RetryOptions.Builder.html
      ;; TODO understand this.
      ;; Problly easier to wrap
      ;; try/catch everywhere and
      ;; return these
      ;; https://www.javadoc.io/doc/io.temporal/temporal-sdk/latest/io/temporal/failure/ApplicationFailure.html
      ;; from clj
      (seq do-not-retry-coll) (.setDoNotRetry (into-array String do-not-retry-coll))
      )))

(defn ^ActivityOptions activity-opts
  "Helper to build an (ActivityOptions)[https://www.javadoc.io/doc/io.temporal/temporal-sdk/latest/io/temporal/activity/ActivityOptions.html]
  example usage:
  (activity-opts
    {:start-to-close-timeout (Duration/ofSeconds 2)
     :retry-options (retry-options {})})
  all entries are optional
  "
  [{:keys [start-to-close-timeout
           retry-options]}]
  (.build
    (cond-> (ActivityOptions/newBuilder)
      start-to-close-timeout (.setStartToCloseTimeout start-to-close-timeout)
      retry-options (.setRetryOptions retry-options))))


(defn activity-stub
  "this can ONLY be called from inside a Workflow implementation
  ie. inside a reified workflow imlementation body
  ex.

  (reify GreetingWorkflow
  (getGreeting [this s]
    (let [actv
          (activity-stub GreetingActivities)]
      (.composeGreeting actv \"HELLO \" s))))
  "
  ;;^IActivities
  ([IActivities]
   (Workflow/newActivityStub IActivities))
  ([IActivities ^ActivityOptions activity-options]
   (Workflow/newActivityStub
     IActivities
     activity-options)))

(defn worker-opts
  "Helper to build an (WorkerOptions)[https://www.javadoc.io/static/io.temporal/temporal-sdk/1.5.0/io/temporal/worker/WorkerOptions.Builder.html]
  example usage:
  (worker-opts
    {:max-concurrent-activity-execution-size 2
     :max-concurrent-workflow-task-execution-size 2
     :max-concurrent-local-activity-execution-size 2
     :activity-poll-thread-count 2
     :default-deadlock-detection-timeout 1000
     :local-activity-worker-only? false
     :max-task-queue-activities-per-second 2
     :max-worker-activities-per-second 2
     })
  all entries are optional
  "
  ^WorkerOptions
  [{:keys [max-concurrent-activity-execution-size
           max-concurrent-workflow-task-execution-size
           max-concurrent-local-activity-execution-size
           activity-poll-thread-count
           default-deadlock-detection-timeout
           max-concurrent-activity-execution-size
           local-activity-worker-only?
           max-task-queue-activities-per-second
           max-worker-activities-per-second
           ]}]
  (.build
    (cond-> (WorkerOptions/newBuilder)
      max-concurrent-activity-execution-size (.setMaxConcurrentActivityExecutionSize max-concurrent-activity-execution-size)
      max-concurrent-workflow-task-execution-size (.setMaxConcurrentWorkflowTaskExecutionSize max-concurrent-workflow-task-execution-size)
      max-concurrent-local-activity-execution-size (.setMaxConcurrentLocalActivityExecutionSize max-concurrent-local-activity-execution-size)
      activity-poll-thread-count (.setActivityPollThreadCount activity-poll-thread-count)
      default-deadlock-detection-timeout (.setDefaultDeadlockDetectionTimeout default-deadlock-detection-timeout)
      local-activity-worker-only? (.setLocalActivityWorkerOnly local-activity-worker-only?) ;; for tests
      max-concurrent-activity-execution-size (.setMaxConcurrentActivityExecutionSize max-concurrent-activity-execution-size)
      max-task-queue-activities-per-second (.setMaxTaskQueueActivitiesPerSecond max-task-queue-activities-per-second)
      max-worker-activities-per-second (.setMaxWorkerActivitiesPerSecond max-worker-activities-per-second)))
  )


;; TODO see is useful
#_(defn activity-interceptor [])

(defn worker-factory-opts
  "Helper to build an (WorkerOptions)[https://www.javadoc.io/static/io.temporal/temporal-sdk/1.5.0/io/temporal/worker/WorkerOptions.Builder.html]
  example usage:
  (worker-factory-opts
    {:enable-logging-in-replay true
  :max-workflow-thread-count 2
  :workflow-cache-size 2
  :workflow-host-local-poll-thread-count 2
  :workflow-host-local-task-queue-schedule-to-start-timeout (Duration/ofSeconds 2)
  :activity-interceptors TODO
     })
  all entries are optional
  "
  ^WorkerFactoryOptions
  [{:keys [enable-logging-in-replay
           max-workflow-thread-count
           workflow-cache-size
           workflow-host-local-poll-thread-count
           workflow-host-local-task-queue-schedule-to-start-timeout
           activity-interceptors
           ]}]
  (.build (cond-> (WorkerFactoryOptions/newBuilder)
            activity-interceptors (.setActivityInterceptors activity-interceptors)
            enable-logging-in-replay (.setEnableLoggingInReplay false)
            max-workflow-thread-count (.setMaxWorkflowThreadCount max-workflow-thread-count)
            workflow-cache-size (.setWorkflowCacheSize workflow-cache-size)
            workflow-host-local-poll-thread-count (.setWorkflowHostLocalPollThreadCount workflow-host-local-poll-thread-count)
            workflow-host-local-task-queue-schedule-to-start-timeout (.setWorkflowHostLocalTaskQueueScheduleToStartTimeout workflow-host-local-task-queue-schedule-to-start-timeout))))


(defn workflow-service-stubs-opts
  "Helper to create a WorkflowServiceStubsOptions"
  ^WorkflowServiceStubsOptions
  [{:keys [target]}]
  (.build
    (cond-> (WorkflowServiceStubsOptions/newBuilder)
      target (.setTarget target))))

(defn workflow-service-stubs
  "Helper to create a WorkflowServiceStubs"
  ^WorkflowServiceStubs
  ([]
   (WorkflowServiceStubs/newInstance))
  ([^WorkflowServiceStubsOptions opts]
   (WorkflowServiceStubs/newInstance opts)))

(defn client-ops
  ^WorkflowClientOptions
  ([] (-> (WorkflowClientOptions/newBuilder)
          (.build)))
  ([{:keys []}]
   (-> (WorkflowClientOptions/newBuilder)
       (.build))))

(defn workflow-client
  ([^WorkflowServiceStubs service]
   (WorkflowClient/newInstance service
                               ))
  ([^WorkflowServiceStubs service ^WorkflowClientOptions opts]
   (WorkflowClient/newInstance service opts)))

(defn worker-factory
  ^WorkerFactory
  ([^WorkflowClient client] (WorkerFactory/newInstance client))
  ([^WorkflowClient client ^WorkerFactoryOptions factory-opts] (WorkerFactory/newInstance client factory-opts)))

(defn new-worker
  ([^WorkerFactory factory ^String task-queue]
   (.newWorker factory task-queue))
  ([^WorkerFactory factory ^String task-queue ^WorkerOptions worker-opts]
   (.newWorker factory task-queue worker-opts)))

(defn component
  "higher-level fn: returns a map suitable for making a stuartsierra/component or similar.
  task-queue: String, the name of the task queue

  opts: options for the subcomponents. all optional.
    service-opts: an instance of WorkflowServiceStubsOptions (can use `workflow-service-stubs-opts` to produce it)
    client-opts: an instance of WorkflowClientOptions (can use `client-ops` to get and instance of WorkflowClientOptions)
    factory-opts: an instance of WorkerFactoryOptions (can use `worker-factory-opts` to get and instance of WorkerFactoryOptions)
    worker-opts: an instance of WorkerOptions (can use `worker-opts` to get and instance of WorkerOptions)
  "
  ([^String task-queue implementation-types activities-impls] (component task-queue implementation-types activities-impls nil))
  ([^String task-queue
    implementation-types
    activities-impls
    {:keys [service-opts client-opts factory-opts worker-opts] :as opts}]
   (let [^WorkflowServiceStubs service (if service-opts
                                         (workflow-service-stubs service-opts)
                                         (workflow-service-stubs))
         ^WorkflowClient client        (if client-opts
                                         (workflow-client service client-opts)
                                         (workflow-client service))
         ^WorkerFactory factory        (if factory-opts
                                         (worker-factory client factory-opts)
                                         (worker-factory client))
         ^Worker worker                (if worker-opts
                                         (new-worker factory task-queue worker-opts)
                                         (new-worker factory task-queue))]

     (.registerWorkflowImplementationTypes worker (into-array implementation-types))
     (.registerActivitiesImplementations worker  (into-array activities-impls))
     (.start factory)

     {:service service
      :client  client
      :factory factory
      :worker  worker})))

(defn stop-component
  ([c] (stop-component c nil))
  ([{:keys [service
            ;client
            factory
            ;worker
            ] :as c}
    {:keys [factory-await-long
            factory-await-timeunit
            service-await-long
            service-await-timeunit] :as opts}]
   (when factory
     (.shutdown factory)
     (.awaitTermination factory (or factory-await-long 1) (or factory-await-timeunit TimeUnit/SECONDS)))

   (when service
     (.shutdown service)
     (.awaitTermination service (or service-await-long 1) (or service-await-timeunit TimeUnit/SECONDS))
     ;; (.shutdownNow service)
     )
   c))
