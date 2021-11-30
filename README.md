# temporal

[temporal.io](http://temporal.io/) (Uber Cadence successor) in Clojure.

This is a library to help using the [temporal java SDK](https://github.com/temporalio/sdk-java) by introducing helpers macros and functions.

## Installation

[net.clojars.nha/temporal](https://clojars.org/nha/temporal)

## Usage

Don't. At least not yet.
If you must, here is the [greetings sample](https://github.com/temporalio/samples-java/blob/main/src/main/java/io/temporal/samples/hello/HelloActivity.java) translated:

```clojure

(ns your.ns
  (:require [nha.temporal :as t])
  (:import [java.time Duration]))

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
      (let [actv (t/activity-stub GreetingActivities
                                  (t/activity-opts {:start-to-close-timeout (Duration/ofSeconds 2)}))]
        (.composeGreeting actv "HELLO " s)))))

(def my-activity (reify GreetingActivities
                   (composeGreeting [this greetings n]
                     (str greetings n))))

(let [{:keys [client] :as component} (t/component task-queue
                                                  [(class my-workflow)]
                                                  [my-activity])]
  (let [^GreetingWorkflow workflow (t/network-stub client
                                                   GreetingWorkflow
                                                   (t/workflow-options task-queue workflow-id))]

    (println (.getGreeting workflow "WORLD")) ;; <= will print "HELLO WORLD"
    )
  (t/stop-component component))

```

## Development

Start a REPL with:

    $ clojure -M:dev

Run the project's tests:

    $ clojure -M:test

## License

Copyright © 2021 Nicolas.ha
