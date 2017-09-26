(ns stateful-testing.fsm-test-utils
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.test.check.generators :as gen]))

(defprotocol Command
  (precondition [this state] "Returns true if command can be applied in current system state")
  (postcondition [this state cmd] "Returns true if cmd can be applied on specified state")
  (exec [this state cmd] "Applies command in the specified system state, returns new state")
  (generate [this state] "Generates command given the current system state, returns command"))


(defn valid-sequence?
  [commands state-seq cmd-seq sub-seq-idxs]
  (when (seq sub-seq-idxs)
    (map? (reduce (fn [curr-state state-idx]
                    (let [cmd (get cmd-seq state-idx)
                          command (get commands (:type cmd))]
                      (if (postcondition command curr-state cmd)
                        (exec command curr-state cmd)
                        (reduced false))))
                  (first state-seq)
                  sub-seq-idxs))))

(defn remove-seq
  [s]
  (map-indexed (fn [index _]
                 (#'clojure.test.check.rose-tree/exclude-nth index s))
               s))

(defn shrink-sequence
  [cmd-seq state-seq commands]
  (letfn [(shrink-subseq [s]
            (when (seq s)
              [(map #(get cmd-seq %) s)
               (->> (remove-seq s)
                    (filter (partial valid-sequence? commands state-seq cmd-seq))
                    (mapv shrink-subseq))]))]
    (shrink-subseq (range 0 (count cmd-seq)))))

(defn cmd-seq-helper
  [state commands size]
  (gen/bind (gen/one-of (->> (vals commands)
                             (filter #(precondition % state))
                             (map #(generate % state))))
            (fn [cmd]
              (if (zero? size)
                (gen/return [[cmd state]])
                (gen/fmap
                  (partial concat [[cmd state]])
                  (cmd-seq-helper (exec (get commands (:type cmd)) state cmd)
                                  commands
                                  (dec size)))))))

(defn cmd-seq
  "generate up to 5 stateful commands using a map of possible commands"
  [state commands]
  (gen/bind (gen/choose 0 5)
            (fn [num-elements]
              (gen/bind (cmd-seq-helper state commands num-elements)
                        (fn [cmd-seq]
                          (let [shrinked (shrink-sequence (mapv first cmd-seq)
                                                          (mapv second cmd-seq)
                                                          commands)]
                            (gen/gen-pure shrinked)))))))

