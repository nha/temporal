(ns nha.temporal.testing
  "kept in a different namespace.
  Requiring this ns means that should be on the classpath"
  (:import
   [io.temporal.activity Activity ActivityInterface ActivityMethod ActivityOptions]
   [io.temporal.client WorkflowClient WorkflowOptions WorkflowClientOptions]
   [io.temporal.common RetryOptions]
   [io.temporal.failure ApplicationFailure]
   [io.temporal.serviceclient WorkflowServiceStubs WorkflowServiceStubsOptions]
   [io.temporal.testing TestEnvironmentOptions TestWorkflowEnvironment] ;; <-- separate dependency
   [io.temporal.worker Worker WorkerFactory WorkerOptions WorkerFactoryOptions]
   [io.temporal.workflow Workflow WorkflowInterface WorkflowMethod]
   [java.lang.annotation Retention RetentionPolicy Target ElementType]
   [java.time Duration]
   [java.util.concurrent TimeUnit]))

(defn test-env-opts
  ^TestEnvironmentOptions
  [{:keys [factory-opts client-opts]}]
  (.build
   (cond-> (TestEnvironmentOptions/newBuilder)
     factory-opts (.setWorkerFactoryOptions factory-opts)
     client-opts  (.setWorkflowClientOptions client-opts))))

(defn test-workflow-env
  ^TestWorkflowEnvironment
  ([]
   (TestWorkflowEnvironment/newInstance))
  ([^TestEnvironmentOptions test-env-opts]
   (TestWorkflowEnvironment/newInstance test-env-opts)))

(defn get-workflow-client [^TestWorkflowEnvironment test-env]
  (.getWorkflowClient test-env))

(defn new-worker ^Worker
  ([^TestWorkflowEnvironment test-env ^String task-queue]
   (.newWorker test-env task-queue))
  ([^TestWorkflowEnvironment test-env ^String task-queue ^WorkerOptions worker-opts]
   (.newWorker test-env task-queue worker-opts)))

(defn test-component
  "higher-level fn: returns a map suitable for making a stuartsierra/component or similar
  similar to the `nha.temporal/component` function, but suitable for tests so that a real service does not have to be reachable

  test-env-opts: TestEnvironmentOptions (use `test-env-opts`)
  worker-opts:
  "
  ([^String task-queue]
   (test-component task-queue nil))
  ([^String task-queue {:keys [test-env-opts worker-opts] :as opts}]
   (let [^TestWorkflowEnvironment test-env (if test-env-opts
                                             (test-workflow-env test-env-opts)
                                             (test-workflow-env))
         ;;^WorkflowServiceStubs service     nil
         ^WorkflowClient client            (get-workflow-client test-env)
         ;;^WorkerFactory factory            nil
         ^Worker worker                    (if worker-opts
                                             (new-worker test-env task-queue worker-opts)
                                             (new-worker test-env task-queue))]
     {:test-env test-env
      ;;:service service
      :client   client
      ;;:factory factory
      :worker   worker})))


(defn start-component [{:keys [test-env worker] :as component}
                       implementation-types
                       activities-impls]
  (.registerWorkflowImplementationTypes worker (into-array implementation-types))
  (.registerActivitiesImplementations worker  (into-array activities-impls))
  (.start test-env)
  component)

(defn stop-component
  [{:keys [test-env] :as c}]
  (when test-env (.close test-env))
  c)
