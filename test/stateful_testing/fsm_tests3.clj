(ns stateful-testing.fsm-tests3
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.test :refer :all]
    [clojure.test.check :as tc]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop]
    [stateful-testing.fsm-test-utils :as fsm]))

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
    (precondition [_ state]
      true)

    (postcondition [_ state cmd]
      true)

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
    (precondition [_ state]
      true)

    (postcondition [_ state cmd]
      true)

    (exec [_ state cmd]
      (apply-command state cmd))

    (generate [_ state]
      (gen/fmap (partial zipmap [:type])
                (gen/tuple (gen/return :clear-cmd))))))

;;-----------------------------------------------------
;;property definition

(defn apply-commands
  [commands]
  (reduce apply-command
          {:ids #{}}
          commands))

(comment
  (gen/sample (fsm/cmd-seq {:ids #{}} {:add-cmd    add-cmd
                                       :delete-cmd delete-cmd
                                       :clear-cmd  clear-cmd})
              3))

(def commands-consistent-apply
  (prop/for-all [commands (fsm/cmd-seq {:ids #{}} {:add-cmd    add-cmd
                                                   :delete-cmd delete-cmd
                                                   :clear-cmd  clear-cmd})]
                (set? (:ids (apply-commands commands)))))

(deftest set-operations-pass
  (is (true? (:result (tc/quick-check 100 commands-consistent-apply)))))