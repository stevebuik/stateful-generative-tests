(ns stateful-testing.fsm-tests4
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.test :refer :all]
    [clojure.test.check :as tc]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop]
    [stateful-testing.fsm-test-utils :as fsm]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]))

(defn apply-command
  "translate a command map into an operation and invoke it, with any required args"
  [state cmd]
  (update-in state [:ids]
             (fn [ids]
               (case (:type cmd)
                 :add-cmd (conj ids (:id cmd))
                 :delete-cmd (disj ids (:id cmd))
                 :clear-cmd #{}))))

(def add-cmd
  (reify
    fsm/Command
    (precondition [_ state] true)
    (postcondition [_ state cmd] true)
    (exec [_ state cmd]
      (apply-command state cmd))
    (generate [_ state]
      (gen/fmap (partial zipmap [:type :id])
                (gen/tuple (gen/return :add-cmd)
                           (gen/such-that #(not (contains? (:ids state) %))
                                          gen/int))))))

(def delete-cmd
  (reify
    fsm/Command
    (precondition [_ state]
      ; must be values present for a delete to be possible
      (seq (:ids state)))
    (postcondition [_ state cmd]
      ;;delete only valid if present in the set
      (->> (:ids state)
           (filter #(= % (:id cmd)))
           seq))
    (exec [_ state cmd]
      (apply-command state cmd))
    (generate [_ state]
      (gen/fmap (partial zipmap [:type :id])
                (gen/tuple (gen/return :delete-cmd)
                           (gen/elements (:ids state)))))))

(def clear-cmd
  (reify
    fsm/Command
    (precondition [_ state] true)
    (postcondition [_ state cmd] true)
    (exec [_ state cmd]
      (apply-command state cmd))
    (generate [_ state]
      (gen/fmap (partial zipmap [:type])
                (gen/tuple (gen/return :clear-cmd))))))

(s/def ::id (s/with-gen int? #(gen/choose 1 10)))
(s/def ::type #{:add-cmd :delete-cmd :clear-cmd})
(s/def ::command (s/keys :req-un [::id ::type]))
(s/def ::commands-stateless (s/coll-of ::command :min-count 1))
(s/def ::commands-stateful (s/with-gen ::commands-stateless
                                       (constantly
                                         (fsm/cmd-seq {:ids #{}} {:add-cmd    add-cmd
                                                                  :delete-cmd delete-cmd
                                                                  :clear-cmd  clear-cmd}))))

(comment
  (gen/sample (fsm/cmd-seq {:ids #{}} {:add-cmd    add-cmd
                                       :delete-cmd delete-cmd
                                       :clear-cmd  clear-cmd})
              3)
  (s/exercise ::command 3)
  (s/exercise ::commands-stateless 3)
  (s/exercise ::commands-stateful 3))

(defn apply-commands
  [commands]
  ;(pprint commands)
  (let [result (reduce apply-command
                       {:ids #{}}
                       commands)]
    (map? result)))
(s/fdef apply-commands
        :args (s/cat :tx-log ::commands-stateful)
        :ret true?)

(comment
  (s/exercise-fn `apply-commands 3)
  (st/check `apply-commands {:clojure.spec.test.check/opts {:num-tests 100}}))

(deftest set-operations-pass
  (let [result (st/check `apply-commands)]
    ;(pprint result)
    (is (true? (get-in (first result) [:clojure.spec.test.check/ret :result]))
        "stateful generative tests all succeeded")))