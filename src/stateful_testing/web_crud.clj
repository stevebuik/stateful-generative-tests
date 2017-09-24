(ns stateful-testing.web-crud
  (:require
    [clojure.pprint :refer [pprint]]
    [compojure.core :refer [routes GET POST]]
    [compojure.route :as route]
    [compojure.handler :as handler]
    [hiccup.core :refer [html]]
    [ring.adapter.jetty :refer [run-jetty]]))

(defn list-page
  [database req]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (html
              [:head
               [:style {}
                "table, th, td { border: 1px solid grey; }"]]
              [:body
               [:div {} "Skateboard Inventory"
                [:br] [:br]
                [:form {:action "/new" :method "POST"}
                 (vec (concat [:table {}
                               [:tr [:th "Type"] [:th "Rating"] [:th "Actions"]]]
                              (map (fn [{:keys [id type rating]}]
                                     [:tr {:class "board"}
                                      [:td type]
                                      [:td rating]
                                      [:td [:a {:class "delete" :id id :href (str "/delete?id=" id)} "Delete"]]])
                                   (vals @database))
                              [[:tr
                                [:td [:input {:type "text" :name "type" :placeholder "Enter Type"}]]
                                [:td [:input {:type "text" :name "rating" :placeholder "Enter Rating"}]]
                                [:td [:button {:type "submit"} "Add board"]]]]))]
                ]])})

(defn delete-record
  [database req]
  (let [id (get-in req [:params :id])]
    (swap! database (fn [db]
                      (reduce-kv (fn [m k v]
                                   (if (= (Integer/parseInt id) k)
                                     m
                                     (assoc m k v)))
                                 {}
                                 db)))
    {:status  303
     :headers {"Location" "/list"}}))

(defn new-record
  [database req]
  (let [{:keys [type rating]} (:params req)]
    (swap! database (fn [db]
                      (let [id (if (seq @database)
                                 (inc (apply max (keys @database)))
                                 1)]
                        (assoc db id {:id     id
                                      :type   type
                                      :rating rating}))))
    {:status  303
     :headers {"Location" "/list"}}))

(defn app
  [db]
  (let [database (or db (atom {1 {:id     1
                                  :type   "Onewheel"
                                  :rating "Sweet As Bro!"}
                               2 {:id     2
                                  :type   "Old Skull"
                                  :rating "Old school ride"}}))]
    (handler/site
      (routes
        (GET "/list" [] (partial list-page database))
        (GET "/delete" [] (partial delete-record database))
        (POST "/new" [] (partial new-record database))
        (route/not-found "Page not found")))))

(comment
  (def server (atom nil))
  (do
    (when-let [s @server]
      (.stop s))
    (reset! server (run-jetty (app nil) {:port 8080 :join? false}))))