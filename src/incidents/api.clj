(ns incidents.api
  (:require [incidents.db :as db]
            [incidents.utils :as utils]
            [incidents.scrape :as scrape]
            [ring.util.response :as rutil]
            [markdown.core :as md]
            [taoensso.timbre :as log]
            [cheshire.core :as json]
            [incidents.reports :as reports]
            [clojure.walk :as walk]
            [compojure.core :as compojure]
            [compojure.route :as route])
  (:import java.util.Date))


(defn json-response
  "Takes some clojure data, encodes it as JSON, wraps it in a ring response,
  adds the right header, and returns it."
  [data]
  {:status 200
   :headers {"Content-Type" "application/json;charset=UTF-8"
             ;; TODO: yeah, OK, maybe ETags might be useful in the future.
             "Cache-Control" "private, no-cache, no-store"}
   :body (json/encode data true)})


(defn serialize-for-json
  [t]
  (walk/postwalk #(if (= java.util.Date (class %))
                    (.getTime %)
                    %)
                 t))


(defn- with-count
  [{:keys [count]} xs]
  (cond->> xs count (take (Integer/parseInt count))))


(defn- convert-dates
  "Takes params map, and returns a map of :min :max dates IFF the input is valid"
  [{:keys [min max]}]
  (when (and min max (every? #(-> % empty? not) [min max]))
    (let [min (Long/parseLong min)
          max (Long/parseLong max)]
      (when (every? (partial < 0) [min max])
        {:min min
         :max max}))))


(defn- with-dates
  [params xs]
  (if-let [{:keys [min max]} (convert-dates params)]
    (filter (fn [{:keys [time]}]
              (let [millis (.getTime time)]
                (and (> millis min)
                     (< millis max))))
            xs)
    xs))

(defn- convert-geos
  "Takes a params map, and returns a map of :chosen-lat and :chosen-lng IFF the input is valid"
  [{:keys [lat lng]}]
  (when (and lat lng (every? #(-> % empty? not) [lat lng]))
    (let [lat (Double/parseDouble lat)
          lng (Double/parseDouble lng)]
      (when (every? (partial not= 0.0 ) [lat lng])
        {:chosen-lat lat
         :chosen-lng lng}))))


(defn- with-geo
  [params xs]
  (if-let [{:keys [chosen-lat chosen-lng]} (convert-geos params)]
    (filter (fn [{{:keys [lat lng]} :geo}]
              (and (= lat chosen-lat)
                   (= lng chosen-lng)))
            xs)
    xs))




(defn- with-types
  [{:keys [typess]} xs]
  ;; TODO: filter #{} set of types supplied
  )

(defn- with-search-string
  "filter results based on search string"
  [{:keys [search]} xs]
  (if search
    (filter (partial utils/simpler-contains  :description search)
            xs)
    xs))

(defn get-all
  [db params]
  (->> db
       vals
       (with-geo params)
       (with-dates params)
       (with-search-string params)
       ;;(with-types params)
       (sort-by :time)
       reverse
       (with-count params) ;; must be last before serializing
       serialize-for-json))


;; Sorry this looks like ass, but it works.
;; Make things as simple as possible, but no simpler.
(defn get-geos
  "Gets only unique geos, summarized by date and count params."
  [db params]
  (let [sorted (->> db
                    vals
                    (with-dates params)
                    (with-search-string params)
                    ;;(with-types params)
                    (sort-by :time)
                    reverse)
        geos (utils/all-keys db :geo)]
    (->> (for [g geos]
           (->> sorted
                (filter #(= g (:geo %)))
                first)) ;; only want the most recent.
         (filter map?) ;; skip the nil's and empties.
         (with-count params)
         serialize-for-json
         )))




;; TODO: api endpoints for (reports/disposition-counts), (reports/type-counts), maybe (reports/address-counts)?
(compojure/defroutes routes
  (compojure/GET "/api" {:keys [params db]}
                 (-> (or db @db/db)
                     (get-all params)
                     json-response))

  
  (compojure/GET "/api/geos" {:keys [params db]}
                 (-> (or db @db/db)
                     (get-geos params)
                     json-response))

  
  ;; TODO: error handling/validation for non-valid keys?
  (compojure/GET "/api/keys/:kind" {{:keys [kind]} :params db :db}
                 (some->> kind
                          keyword
                          (utils/all-keys (or db @db/db))
                          vec
                          json-response))

  

  ;; TODO: error handling/validation for non-valid keys?
  (compojure/GET "/api/stats/:kind" {{:keys [kind]} :params db :db}  
                 (some->> kind
                          keyword 
                          (utils/key-set-counts (or db @db/db))
                          json-response))


  
  (compojure/GET "/api/dates"  {:keys [params db]}
                 (-> (or db @db/db)
                     reports/timestamps-min-max
                     json-response))

  
  ;; should really be a PUT or something, but whatever.
  (compojure/GET "/api/scrape" {:keys [params db]}
                 (-> (or db @db/db)
                     scrape/start-pdf-downloading
                     (pr-str "running")
                     json-response))

  (compojure/GET "/api/docs" {:keys [params db]}
                 (-> "doc/API.md"
                     slurp
                     md/md-to-html-string))
  
  (compojure/GET "/api/status" {:keys [params db]}
                 (-> (or db @db/db)
                     reports/quick-status
                     json-response)))



(comment






  )
