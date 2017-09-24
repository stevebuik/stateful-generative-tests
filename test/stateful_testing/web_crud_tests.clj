(ns stateful-testing.web-crud-tests
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.test :refer :all]
    [kerodon.test :as t]
    [kerodon.core :as k]
    [net.cgrand.enlive-html :as html]
    [stateful-testing.web-crud :as app]
    [net.cgrand.enlive-html :as enlive]
    [clojure.spec.alpha :as s]
    [clojure.test.check.generators :as gen]
    [stateful-testing.fsm-test-utils :as fsm]))

;;; DOM MANIPULATION FNS

(defn get-ids
  "scrape the list page for a list of ids present in the database"
  [session]
  (-> session
      (k/visit "/list")
      (t/has (t/status? 200))
      :enlive
      (html/select [:tr.board :a])
      (->> (map :attrs)
           (map :id))))

(defn add
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

;;; TEST UTILS

(s/def ::id (s/with-gen int? #(gen/choose 1 10)))
(s/def ::type #{:add-cmd :delete-cmd})
(s/def ::board-type string?)
(s/def ::board-rating string?)

(defmulti command ::type)
(s/def ::command (s/multi-spec command ::type))

(defmethod command :add-cmd
  [_]
  (s/keys :req-un [::type ::board-type ::board-rating]))

(defmethod command :delete-cmd
  [_]
  (s/keys :req-un [::type ::id]))

(s/def ::commands-stateless (s/coll-of ::command :min-count 1))

(defn apply-command
  "apply a cmd to a running webapp using a Kerodon session"
  [session cmd]
  (case (:type cmd)
    :add-cmd (add session (:board-type cmd) (:board-rating cmd))
    :delete-cmd (delete session (:id cmd))))

(def add-cmd
  (reify
    fsm/Command
    (precondition [_ session] true)
    (postcondition [_ session cmd] true)
    (exec [_ session cmd]
      (apply-command session cmd))
    (generate [_ session]
      (gen/fmap (partial zipmap [:type :board-type :board-rating])
                (gen/tuple (gen/return :add-cmd)
                           gen/string
                           gen/string)))))

(def delete-cmd
  (reify
    fsm/Command
    (precondition [_ session]
      ; must be values present for a delete to be possible
      (seq (get-ids session)))
    (postcondition [_ session cmd]
      ;;delete only valid if present in the scraped ids
      (->> (get-ids session)
           (filter #(= % (:id cmd)))
           seq))
    (exec [_ session cmd]
      (apply-command session cmd))
    (generate [_ session]
      (gen/fmap (partial zipmap [:type :id])
                (gen/tuple (gen/return :delete-cmd)
                           (gen/elements (get-ids session)))))))

(comment
  (s/exercise ::command 1)
  (s/exercise ::commands-stateless 3)                       ; errors from multi-spec. ignoring
  (let [db (atom {})
        web-app (app/app db)
        stateful-kerodon-session (k/session web-app)]
    (gen/sample (fsm/cmd-seq stateful-kerodon-session {:add-cmd    add-cmd
                                                       :delete-cmd delete-cmd})
                3)))

(s/def ::commands-stateful (s/with-gen ::commands-stateless
                                       (constantly
                                         (let [db (atom {})
                                               web-app (app/app db)
                                               stateful-kerodon-session (k/session web-app)]
                                           (fsm/cmd-seq stateful-kerodon-session {:add-cmd    add-cmd
                                                                                  :delete-cmd delete-cmd})))))

(defn apply-tx
  [tx-log]
  (let [db (atom {})
        web-app (app/app db)
        stateful-kerodon-session (k/session web-app)
        result (reduce apply-command
                       stateful-kerodon-session
                       tx-log)]
    (map? result)))
(s/fdef apply-tx
        :args (s/cat :tx-log ::commands-stateful))

(comment
  (s/exercise ::commands-stateful 1)
  (s/exercise-fn `apply-tx 3)
  (st/check `apply-tx {:clojure.spec.test.check/opts {:num-tests 100}}))
