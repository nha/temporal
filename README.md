# temporal

[temporal.io](http://temporal.io/) (Uber Cadence successor) in Clojure.

This is a library to help using the [temporal java SDK](https://github.com/temporalio/sdk-java) by introducing helpers macros and functions.

## Installation

[net.clojars.nha/temporal](https://clojars.org/nha/temporal)

## Usage

Don't. At least not yet.
If you must, here is the greetings sample translated:

```clojure

(:require [nha.temporal :as t])

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
      (sut/component task-queue
                         [(class reified-workflow)]
                         [my-activity])]
        (let [^GreetingWorkflow workflow (sut/network-stub client
                                                       GreetingWorkflow
                                                       (sut/workflow-options task-queue workflow-id))]


(println (.getGreeting workflow "WORLD")) ;; will print "HELLO WORLD"
      )                 
                         
       (sut/stop-component component))
        
...
```


## Goals




## Development

Start a REPL with:

    $ clojure -M:dev

Run the project's tests:

    $ clojure -M:test

## License

Copyright © 2021 Nicolas.ha
