(ns stateful-testing.fsm-tests2
  (:require
    [clojure.pprint :refer [pprint]]
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
      true)

    (postcondition [_ state cmd]
      true)

    (exec [_ state cmd]
      (update-in state [:ids] conj (:id cmd)))

    (generate [_ state]
      (gen/fmap (partial zipmap [:type :id])
                (gen/tuple (gen/return :add-cmd)
                           (gen/such-that #(not (contains? (:ids state) %))
                                          gen/int))))))

(def delete-cmd
  (reify
    fsm/Command
    (precondition [_ state]
      (seq (:ids state)))

    (postcondition [_ state cmd]
      ;;delete only valid if present in the set
      (->> (:ids state)
           (filter #(= % (:id cmd)))
           seq))

    (exec [_ state cmd]
      (update-in state [:ids] (fn [ids]
                                (->> ids
                                     (remove #{(:id cmd)})
                                     set))))

    (generate [_ state]
      (gen/fmap (partial zipmap [:type :id])
                (gen/tuple (gen/return :delete-cmd)
                           (gen/elements (:ids state)))))))

(def clear-cmd
  (reify
    fsm/Command
    (precondition [_ state]
      true)

    (postcondition [_ state cmd]
      true)

    (exec [_ state cmd]
      (update-in state [:ids] (constantly #{})))

    (generate [_ state]
      (gen/fmap (partial zipmap [:type])
                (gen/tuple (gen/return :clear-cmd))))))

;;-----------------------------------------------------
;;property definition

(defn apply-tx
  [tx-log]
  (reduce (fn [ids {:keys [id type] :as cmd}]
            (case type
              :add-cmd (conj ids id)
              :delete-cmd (set (remove #{id} ids))))
          {:ids #{}}
          tx-log))

(def commands-consistent-apply
  (prop/for-all [tx-log (fsm/cmd-seq {:ids #{}} {:add-cmd    add-cmd
                                                 :delete-cmd delete-cmd
                                                 :clear-cmd  clear-cmd})]
                (not (nil? (apply-tx tx-log)))))

(deftest set-operations-pass
  (is (:result (tc/quick-check 100 commands-consistent-apply))))