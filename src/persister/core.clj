(ns persister.core
  (:require [clojure.data :as data]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.tools.cli :as cli]
            [clojure.tools.logging :as log])
  (:import java.util.Properties
           [org.apache.kafka.clients.consumer KafkaConsumer Consumer ConsumerRecord])
  (:gen-class))

(def configuration
  {"bootstrap.servers" (System/getenv "BOOTSTRAP_SERVERS")
   "group.id" (System/getenv "PERSISTER_GROUP")
   "enable.auto.commit" "true"
   "auto.commit.interval.ms" "1000"
   "key.deserializer" "org.apache.kafka.common.serialization.StringDeserializer"
   "value.deserializer" "org.apache.kafka.common.serialization.StringDeserializer"
   "security.protocol" "SSL"
   "ssl.truststore.location" (System/getenv "PERSISTER_TRUSTSTORE_LOCATION")
                               ;;"keys/dev.persister.client.keystore.jks")
   "ssl.truststore.password" (System/getenv "PERSISTER_TRUSTSTORE_PASSWORD")
                               ;; specified via GCE environement variable
   "ssl.keystore.location" (System/getenv "PERSISTER_KEYSTORE_LOCATION")
                               ;; "keys/dev.persister.client.keystore.jks")
   "ssl.keystore.password" (System/getenv "PERSISTER_KEYSTORE_PASSWORD")
                               ;; specified via GCE environment variable
   "ssl.key.password" (System/getenv "PERSISTER_KEY_PASSWORD")
                               ;; specified via GCE environment variable
   "ssl.endpoint.identification.algorithm" ""
   })

(defn client-configuration 
  "Create client configuration as Java Properties"
  []
  (let [props (new Properties)]
    (doseq [p configuration]
      (.put props (p 0) (p 1)))
    props))

(defn consumer
  "Create a kafka consumer."
  []
  (let [props (client-configuration)]
    (new KafkaConsumer props)))

(defn get-topics
  "Get the current list of all configured topics (including internal)"
  []
  (try
    (with-open [consumer (consumer)]
      (let [topicmap (.listTopics consumer)
            topics (keys topicmap)]
        (log/info "get-topics found topics" topics)
        (set topics)))
    (catch Exception e
      (log/info "Caught exception getting topics: " (.getMessage e))
      #{})))

(defn filter-internal
  "Filter out any kafka internal topics"
  [topic-set]
  (set (remove #(str/starts-with? % "__") topic-set)))

(defn filter-topics
  "Filter the excluded topics from the command line from the list of monitored topics"
  [topic-set exclude-set include-set]
  (if-not (empty? include-set)
    include-set
    (set/difference topic-set exclude-set)))

(defn diff-topics
  "Produce a diff set of the current known topics, and the list provided by calling get-topics"
  [extant-topic-set exclude-set include-set]
  (let [all-topics (get-topics)
        filtered-internal-topics (filter-internal all-topics)
        topics (filter-topics filtered-internal-topics exclude-set include-set)
        diffs (data/diff extant-topic-set topics)
        dels (nth diffs 0) ;; may be nil
        adds (nth diffs 1)] ;; may be nil
    (log/info "Topics to monitor" topics)
    {:adds adds :deletes dels :all topics}))

(defn write-to-file
  "Write the kafka record to a file in json format"
  [msg output-dir]
  (let [topic (.topic msg)
        partition (.partition msg)
        offset (.offset msg)
        create-time (.timestamp msg)
        key (.key msg)
        value (.value msg)
        template (str topic "-" partition "-" offset ".json")
        file (str output-dir "/" template)
        json-str (json/write-str {"topic" topic
                                  "partition" partition
                                  "offset" offset
                                  "key" key
                                  "value" value
                                  "createTime" create-time
                                  })
        contents (str json-str "\n")]
    (spit file contents)
    (log/info (str "Wrote" contents "to file" file))))

(defn process-kafka-messages
  "Read messages from data exchange for a single topic"
  [topic output-dir]
  (log/info "opening consumer for" topic)
  (with-open [consumer (consumer)]
    (log/info "Subscribing to topic" topic)
    (.subscribe consumer [topic])
    (while true 
      (let [records (.poll consumer 1000)]
        (doseq [r (seq records)]
          (try
            (log/info "Received a message on topic" topic r)
            (write-to-file r output-dir)
            (catch Exception e
              (log/info "Caught exception processing messages: " (.getMessage e)))))))))

(defn as-future
  "Creates a message processor as a future"
  [topic output-dir]
  (let [task (future (process-kafka-messages topic output-dir))]
    task))

(defn main-loop
  "Main loop that calls kafka-topics --list and creates/deletes consumer threads"
  [options]
  (def consumer-map (atom {}))
  (loop [internal-topics #{}]
    (let [exclude-str  (:exclude-list options)
          exclude-set (set (remove empty? (str/split exclude-str #",")))
          include-str  (:include-list options)
          include-set (set (remove empty? (str/split include-str #",")))
          output-dir (:output-dir options)
          sleeptime (:sleeptime options)
          topic-map (diff-topics internal-topics exclude-set include-set)
          adds (:adds topic-map)
          dels (:deletes topic-map)
          all-topics (:all topic-map)
          changes (some? (set/union adds dels))]
      (when (true? changes)
        ;; not sure we actually get deletes - it appears that a topic
        ;; isn't deleted when there is an active listener on it
        (when (some? dels)
          (log/info (str "Deleting topic consumers: " (seq dels)))
          (doseq [deleted-topic (seq dels)]
            (def task (get @consumer-map deleted-topic)) 
            (swap! consumer-map (fn [curr-map] (dissoc curr-map deleted-topic)))
            (future-cancel task)))
        ;; topics to add listeners onto
        (when (some? adds)
          (log/info (str "Adding topic consumers: " (seq adds)))
          (doseq [added-topic (seq adds)]
            (def task (as-future added-topic output-dir))
            (swap! consumer-map (fn [curr-map] (assoc curr-map added-topic task)))
            (future-call task))))
      (Thread/sleep sleeptime)
      (recur all-topics))))

(def cli-options [["-e" "--exclude list" "Comma separated list of topics to exclude"
                   :id :exclude-list
                   :default ""]
                  ["-h" "--help" "Display help"
                   :id :help]
                  ["-i" "--include list" "Comma separated list of topics to include (ignores exclude option)"
                   :id :include-list
                   :default ""]
                  ["-s" "--sleep" "Topic list check sleep time in milli-seconds (default 2 mins)"
                   :parse-fn #(Integer. %)
                   :default "120000"
                   :id :sleeptime]
                  ["-o" "--output dir" "The directory to which message files will be output (required)"
                   :id :output-dir]])

(defn process-opts
  "Process the command line arguments"
  [parsed-opts]
  (let [options (:options parsed-opts)
        errors (:errors parsed-opts)
        summary (:summary parsed-opts)
        output-dir (:output-dir options)
        help (:help options)]
    (if help
      (println summary)
      (do (when-not output-dir
            (println "Error: an output firectory must be specified (-o option)\n" summary))
          (when errors
            (println "ERROR:" errors summary))))
    (not (or help errors (not output-dir)))))  

(defn -main
  [& args]
  (log/info "Kafka message persister started.")
  (let [parsed-opts (cli/parse-opts args cli-options)
        proceed (process-opts parsed-opts)]
    (when proceed
      (main-loop (:options parsed-opts)))))
