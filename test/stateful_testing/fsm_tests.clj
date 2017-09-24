(ns stateful-testing.fsm-tests
  (:require
    [clojure.test :refer :all]
    [clojure.test.check :as tc]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop]
    [clojure.test.check.rose-tree :as rose]
    [stateful-testing.fsm-test-utils :as fsm]))

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

;;-----------------------------------------------------
;;property definition

(defn apply-tx
  "Apply transactions fails when there are two delete commands"
  [tx-log]
  (->> tx-log
       (filter #(= :delete-cmd (:type %)))
       count
       (> 2)))

(def commands-consistent-apply
  (prop/for-all [tx-log (fsm/cmd-seq {:people []} {:add-cmd add-cmd :delete-cmd delete-cmd})]
                (false (apply-tx tx-log))))

(deftest apply-commands
  (is (:result (tc/quick-check 100 commands-consistent-apply))))