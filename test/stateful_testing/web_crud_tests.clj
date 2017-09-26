(ns stateful-testing.web-crud-tests
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.test :refer :all]
    [clojure.spec.test.alpha :as st]
    [kerodon.test :as t]
    [kerodon.core :as k]
    [net.cgrand.enlive-html :as html]
    [stateful-testing.web-crud :as app]
    [net.cgrand.enlive-html :as enlive]
    [clojure.spec.alpha :as s]
    [clojure.test.check.generators :as gen]
    [stateful-testing.fsm-test-utils :as fsm]))

;;; DOM MANIPULATION FNS

(defn add
  "add a board using the webapp"
  [session type rating]
  (-> session
      (k/visit "/list")
      (t/has (t/status? 200))
      (k/fill-in [:input (html/attr= :name "type")] type)
      (k/fill-in [:input (html/attr= :name "rating")] rating)
      ; NOTE: button must have {:type "submit"} to be found by Kerodon
      (k/press [:button])
      (t/has (t/status? 303))
      k/follow-redirect))

(defn delete
  "delete a board (by id) using the webapp"
  [session id]
  (-> session
      (k/visit "/list")
      (t/has (t/status? 200))
      (k/follow [[:a.delete (html/attr= :id (str id))]])
      (t/has (t/status? 303))
      k/follow-redirect))

; test the webapp using a traditional kerodon example-based test
(deftest crud-unit-test
  (let [db (atom {1 {:id     1
                     :type   "Onewheel"
                     :rating "Sweet As Bro!"}})
        session (k/session (app/app db))]

    (-> session
        (k/visit "/list")
        (t/has (t/some-text? "Onewheel") "initial record visible in list")
        (t/has (t/missing? [(enlive/text-pred #{"Skull Board"})]) "new record not visible")

        (add "Skull Board" "Flintstones motor")
        (t/has (t/some-text? "Onewheel") "initial record visible in list")
        (t/has (t/some-text? "Skull Board") "added record visible in list")

        (add "Boosted Board" "good for roads")
        (t/has (t/some-text? "Onewheel") "initial record visible in list")
        (t/has (t/some-text? "Skull Board") "added record visible in list")
        (t/has (t/some-text? "Boosted Board") "added record visible in list")

        (delete 2)
        (t/has (t/some-text? "Onewheel") "initial record visible in list")
        (t/has (t/missing? [(enlive/text-pred #{"Skull Board"})]) "deleted record not visible")
        (t/has (t/some-text? "Boosted Board") "added record visible in list"))))

;;; SPEC BASED COMMAND GENERATION TESTS

(s/def ::id (s/with-gen int? #(gen/pos-int)))
(s/def ::type #{:add-cmd :delete-cmd})
(s/def ::board-type string?)
(s/def ::board-rating string?)

; use a multi-spec since keys are different for add vs delete

(defmulti command :type)
(s/def ::command (s/multi-spec command ::type))

(defmethod command :add-cmd
  [_]
  (s/keys :req-un [::type ::board-type ::board-rating]))

(defmethod command :delete-cmd
  [_]
  (s/keys :req-un [::type ::id]))

(s/def ::commands-stateless (s/coll-of ::command :min-count 1))

(def add-cmd
  (reify
    fsm/Command
    (precondition [_ state] true)
    (postcondition [_ state cmd] true)
    (exec [_ state cmd]
      (conj state (if (seq state)                           ; this emulates the db generating ids
                    (inc (apply max state))
                    1)))
    (generate [_ state]
      (gen/fmap (partial zipmap [:type :board-type :board-rating])
                (gen/tuple (gen/return :add-cmd)
                           gen/string
                           gen/string)))))

(def delete-cmd
  (reify
    fsm/Command
    (precondition [_ state]
      ; must be values present for a delete to be possible
      (seq state))
    (postcondition [_ state cmd]
      ;;delete only valid if present
      (->> state
           (filter #(= % (:id cmd)))
           seq))
    (exec [_ state cmd]
      (->> state
           (remove #(= % (:id cmd)))
           set))
    (generate [_ state]
      (gen/fmap (partial zipmap [:type :id])
                (gen/tuple (gen/return :delete-cmd)
                           (gen/elements state))))))

(comment
  ; TODO generator errors from multi-spec supplied generator. ignoring because using custom generator
  (s/exercise ::command 1)
  (s/exercise ::commands-stateless 3)

  ; can ignore errors from above because we are using a custom/stateful generator instead
  (gen/sample (fsm/cmd-seq #{} {:add-cmd    add-cmd
                                :delete-cmd delete-cmd})
              3)
  (s/exercise ::commands-stateless 3
              {::commands-stateless #(fsm/cmd-seq #{} {:add-cmd    add-cmd
                                                       :delete-cmd delete-cmd})}))

(s/def ::commands-stateful (s/with-gen ::commands-stateless
                                       #(fsm/cmd-seq #{} {:add-cmd    add-cmd
                                                          :delete-cmd delete-cmd})))

; this fn translates from a command into a kerodon fn call i.e. this is the command adaptor
(defn apply-command
  "apply a cmd to a running webapp using a Kerodon session"
  [session cmd]
  (case (:type cmd)
    :add-cmd (add session (:board-type cmd) (:board-rating cmd))
    :delete-cmd (delete session (:id cmd))))

; this fn is the main test driver. because it is spec'd, it can be automatically tested
(defn apply-commands
  [commands]
  ;(pprint commands) ; <<< uncomment this to see the generated commands
  (let [db (atom {})
        web-app (app/app db)
        stateful-kerodon-session (k/session web-app)
        result (reduce apply-command
                       stateful-kerodon-session
                       commands)]
    (map? result)))
(s/fdef apply-commands
        :args (s/cat :cmds ::commands-stateful)
        :ret true?)

(comment
  (s/exercise ::commands-stateful 3)
  (s/exercise-fn `apply-commands 3)
  (st/check `apply-commands {:clojure.spec.test.check/opts {:num-tests 20}}))

(deftest generative-and-stateful
  (let [result (st/check `apply-commands
                         {:clojure.spec.test.check/opts {:num-tests 50}})]
    ;(pprint result) ; uncomment to see failure details i.e. shrunk cmd seq
    (is (-> result
            first
            (get-in [:clojure.spec.test.check/ret :result])
            ; any exception will be in :result so only allow boolean true
            true?)
        "generative tests passed without errors")))

(deftest example-test-found
  ; uncomment to run a shrunk command seq that fails.
  ; found/fixed bug during dev where gen phase model used wrong initial id i.e. webapp uses 1 for first id
  #_(let [db (atom {})
          session (k/session (app/app db))]
      (reduce apply-command
              session
              [{:type :add-cmd, :board-type "", :board-rating ""}
               {:type :add-cmd, :board-type "", :board-rating ""}
               {:type :delete-cmd, :id 0}])))
