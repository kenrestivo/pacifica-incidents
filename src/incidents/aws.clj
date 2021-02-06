(ns incidents.aws
  (:require [incidents.db :as db]
            [incidents.utils :as utils]
            [cognitect.aws.credentials :as credentials]
            [taoensso.timbre :as log]
            [cheshire.core :as json]
            [clojure.walk :as walk]
            [utilza.log :as ulog]
            [utilza.aws :as uaws]
            [org.parkerici.multitool.core :as u]
            [cognitect.aws.client.api :as aws]
            ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment

  (def services (uaws/make-services [:s3 :textract]
                                    {:credentials-provider (credentials/profile-credentials-provider "restivo")}))


  (ulog/spewer
   (uaws/list-ops services :textract))

  (ulog/spewer
   (uaws/docs services :textract :AnalyzeDocument))

  )


(defn invoke-paged
  "Takes a service, a map of options to pass to invoke, and a key to find the actual results.
  Calls invoke with those args on an AWS function.
  Pages through all the results.
  Returns the aggregate results, which will be result-key"
  [service invoke-options result-key]
  (loop [acc {}]
    (print ".")
    (let [res (aws/invoke service (if (-> acc :NextToken empty?)
                                    invoke-options
                                    (assoc-in invoke-options [:request :NextToken] (:NextToken acc))))
          new-acc (-> acc
                      (update-in [result-key] concat (result-key res))
                      (assoc :NextToken (:NextToken res)))]
      (when (:ErrorResponse res)
        (throw (ex-info "AWS Call Failed" res)))
      (if (-> new-acc :NextToken empty?)
        (result-key new-acc)
        (recur new-acc)))))

;;; Fixed version of fn from utiliza
(defn invoke-paged
  "Takes a service, a map of options to pass to invoke, and a key to find the actual results.
  Calls invoke with those args on an AWS function.
  Pages through all the results.
  Returns the aggregate results, which will be result-key"
  [service invoke-options result-key]
  (loop [acc []
         options invoke-options]
    (print ".")
    (let [res (aws/invoke service options)
          new-acc (concat acc (result-key res))]
      (when (:ErrorResponse res)
        (throw (ex-info "AWS Call Failed" res)))
      (if (-> res :NextToken empty?)
        new-acc
        (recur new-acc (assoc-in options [:request :NextToken] (:NextToken res)))))))


(u/defn-memoized client []
  (aws/client {:api :textract
                :region "us-west-2"
                :credentials-provider (credentials/profile-credentials-provider "default")}))

(defn job-id->blocks
  [job-id]
  (invoke-paged
   (client)
   {:op :GetDocumentAnalysis
    :request {:JobId job-id}}
   :Blocks))


