(ns incidents.api
  (:require [liberator.core :as liberator]
            [incidents.db :as db]
            [taoensso.timbre :as log]
            [incidents.reports :as reports]
            [clojure.walk :as walk]
            [compojure.core :as compojure]
            [compojure.route :as route])
  (:import java.util.Date))


(defn serialize-for-json
  [t]
  (walk/postwalk #(if (= java.util.Date (class %))
                    (.getTime %)
                    %)
                 t))


(defn- with-count
  [count xs]
  (cond->> xs count (take (Integer/parseInt count))))

(defn get-all [{:keys [count]}]
  (->> @db/db
       vals
       (sort-by :time)
       reverse
       (with-count count)
       serialize-for-json))


(liberator/defresource incidents
  :method-allowed? (liberator/request-method-in :get)
  
  :available-media-types ["application/json"
                          ;; application/clojure ;; could support edn, but why really?
                          ]
  :see-other (fn [context]
               (:new-url context))
  :handle-ok (fn [{{:keys [params]} :request}]
               (log/debug params)
               (get-all params)))



(liberator/defresource status
  :method-allowed? (liberator/request-method-in :get)
  :available-media-types ["application/json"
                          ;; application/clojure ;; could support edn, but why really?
                          ]
  :handle-ok (fn [{{:keys [params]} :request}]
               (reports/quick-status)))

(compojure/defroutes routes
  (route/files "/static" {:root "resources"})
  (compojure/ANY "/incidents" [] incidents) ;; depreciated
  (compojure/ANY "/api" [] incidents)
  (compojure/ANY "/api/status" [] status))


(comment




  

  )
