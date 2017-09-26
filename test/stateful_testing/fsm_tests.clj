(ns stateful-testing.fsm-tests
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.test :refer :all]
    [clojure.test.check :as tc]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop]
    [clojure.test.check.rose-tree :as rose]
    [stateful-testing.fsm-test-utils :as fsm]
    [clojure.spec.alpha :as s]))

(def add-cmd
  (reify
    fsm/Command
    (precondition [_ state]
      (vector? (:people state)))

    (postcondition [_ state cmd]
      ;;add only valid if no other person with same id
      (->> (:people state)
           (filter #(= (:id %) (:id cmd)))
           seq
           nil?))

    (exec [_ state cmd]
      (update-in state [:people] (fn [people]
                                   (conj people
                                         (dissoc cmd :type)))))

    (generate [_ state]
      (gen/fmap (partial zipmap [:type :name :id])
                (gen/tuple (gen/return :add-cmd)
                           (gen/not-empty gen/string-alphanumeric)
                           (gen/such-that #(-> (mapv :id (:people state))
                                               (contains? %)
                                               not)
                                          gen/int))))))

(def delete-cmd
  (reify
    fsm/Command
    (precondition [_ state]
      (seq (:people state)))

    (postcondition [_ state cmd]
      ;;delete only valid if existing person with id
      (->> (:people state)
           (filter #(= (:id %) (:id cmd)))
           seq))

    (exec [_ state cmd]
      (update-in state [:people] (fn [people]
                                   (vec (filter #(not= (:id %)
                                                       (:id cmd))
                                                people)))))

    (generate [_ state]
      (gen/fmap (partial zipmap [:type :id])
                (gen/tuple (gen/return :delete-cmd)
                           (gen/elements (mapv :id (:people state))))))))

(defn not-many-deletes
  "returns true when there are < 2 delete commands"
  [commands]
  (->> commands
       (filter #(= :delete-cmd (:type %)))
       count
       (> 2)))

(def commands-will-not-fail
  (prop/for-all [commands (fsm/cmd-seq {:people []} {:add-cmd    add-cmd
                                                     :delete-cmd delete-cmd})]
                (true? (not-many-deletes commands))))


(deftest apply-commands-fails
  (let [result (tc/quick-check 100 commands-will-not-fail)]
    ;(pprint result) ; <<<<< uncomment this to see the failing case
    (is (-> result :result false?)
        "commands fail because generator will eventually generate 2 deletes")))