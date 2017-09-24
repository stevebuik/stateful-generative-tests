(ns stateful-testing.states-lib-tests
  (:require
    [clojure.test :refer :all]
    [clojure.pprint :refer [pprint]]
    [clojure.test.check :refer [quick-check]]
    [clojure.test.check.generators :as gen]
    [states.core :refer [run-commands]]
    [stateful-testing.core :refer :all]))

; NOTES : uses a parallel clj structure to track state

(defn commands [{:keys [set class]}]
  (if set
    (gen/tuple (gen/elements `[set-contains set-add set-remove])
               (gen/return set)
               (gen/fmap #(mod % 10) gen/int))
    (gen/return [`new-set class])))

(defn next-step [state var [fn _ elem]]
  (condp = fn
    `set-remove (update-in state [:elems] disj elem)
    `set-add (update-in state [:elems] conj elem)
    `new-set (assoc state :set var)
    state))

(defn postcondition [{:keys [elems]} [fn _ elem] value]
  (if (= fn `set-contains)
    (= value (contains? elems elem))
    true))

(deftest states-lib
  (let [result (quick-check 1000 (run-commands commands next-step postcondition
                                               {:init-state {:elems #{}
                                                             :class java.util.HashSet}}))]
    (is (true? (:result result)))
    (when-not (true? (:result result))
      (pprint result))))


