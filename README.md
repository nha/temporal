# temporal

[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.nha/temporal.svg)](https://clojars.org/org.clojars.nha/temporal)

[temporal.io](http://temporal.io/) (Uber Cadence successor) in Clojure.

This is a library to help using the [temporal java SDK](https://github.com/temporalio/sdk-java) by introducing helpers macros and functions.

## Installation

[net.clojars.nha/temporal](https://clojars.org/nha/temporal)

The versionning is comprised of the temporal java version used followed by the number of commits in this projects.
For example version `1.5.0.7` of `nha/temporal` means the temporal java version used is `1.5.0` and there are `7` commits in this repository.

## Usage

Status: Alpha

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
  (t/stop-component component))

```

## Development

Start a REPL with:

    $ clojure -M:dev

Run the project's tests:

    $ clojure -M:test

Deploy (on the `main` branch):

    $ export CLOJARS_USERNAME=username
    $ export CLOJARS_PASSWORD=clojars-token
    $ export CI=true # optional, if using temporal-testing
    $ clj -T:build ci && clj -T:build install && clj -T:build deploy

## License

Copyright © 2022 Nicolas.ha
