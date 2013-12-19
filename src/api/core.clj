(ns api.core
  (:use [compojure.core :only (defroutes GET)]
        ring.util.response
        ring.middleware.cors
        org.httpkit.server
        [datomic.api :only [db q] :as d])
  (:require [liberator.core :refer [resource defresource]]
            [ring.middleware.params :refer [wrap-params]]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [ring.middleware.reload :as reload]
            [taoensso.timbre :as timbre]
            [cheshire.core :refer :all]))

;;init timbre
(timbre/refer-timbre)

(def clients (atom {}))

;;(def uri "datomic:mem://localhost:4334/platform")
(def uri "datomic:ddb-local://localhost:8000/platform/poc?aws_access_key_id=[fakeaccesskeyid]&aws_secret_key=[fakesecretkey]")

(def conn (d/connect uri))

;;get handlers - doing this outside the scope of defroutes allows 'live' coding

(defn get-hello [name]
  (str "hi " name))

(defn get-products-bg []
 (generate-string (map #(.touch (d/entity (db conn) (first %)))
                                (d/q '[:find ?e 
                                       :where [?e :product/original-id]] 
                                     (db conn)))))

(defn get-companies-bg  []
 (generate-string (map #(.touch (d/entity (db conn) (first %)))
                                (d/q '[:find ?e 
                                       :where [?e :company/uuid]] 
                                     (db conn)))))

(defn get-company-by-id [id]
  (generate-string (.touch (d/entity (db conn)
                    (ffirst (d/q '[:find ?e
                                   :in $ ?oid
                                   :where [?e :company/original-id ?oid]] 
                                 (db conn) (Integer/parseInt id)))))))

(defn get-product-by-id [id]
  (generate-string (.touch (d/entity (db conn)
                    (ffirst (d/q '[:find ?e 
                                   :in $ ?oid
                                   :where [?e :product/original-id ?oid]] 
                                 (db conn) (Integer/parseInt id)))))))


;; (defn get-products-by-index-key [key]
;;   (let [entity-ids (d/q '[find ?e in $ ?key :where
;;                           [?e :product/name ?key]] (db conn) key)
;;         entities   (map #(.touch (d/entity (db conn) (first %)) entity-ids))])

;;   generate-string entities)

;;todo: generalize pattern by specifying entity to be search
;;todo: add search field as a parameter
;; (defn get-entities-by-index-key [type index key]
;;   (let [field (keyword )])


;; )


;;liberator resource definitions  - for production
(defresource get-liberator-hello [name]
  :available-media-types ["application/json"]
  :handle-ok (fn [_] (str "hello " name)))

(defresource get-companies []
  :available-media-types ["application/json"]
  :handle-ok (get-companies-bg))

(defresource get-products []
   :available-media-types ["application/json"]
   :handle-ok (get-products-bg))

(defroutes routes

  ;;hello world
  (GET "/hello/:name" [name]
       (get-liberator-hello name))
  ;;--------------------------------------------------------------------------------------------

  ;;products
  (GET "/api/0.2/products"
       []
       (get-products))

  (GET "/api/0.2/products/:id"
       [id]
       (get-product-by-id id))

  ;;TODO: index function w/partial key lookup
  ;;TODO: fulltext search?
  ;; (GET "/api/0.2/products/index/:key"
  ;;      [key]
  ;;      (get-products-by-index-key key))

  ;;--------------------------------------------------------------------------------------------

  ;;companies
  (GET "/api/0.2/companies"
       []
       (get-companies))

  (GET "/api/0.2/companies/:id"
       [id]
       (get-company-by-id id)))


  ;;TODO: index function w/partial key lookup
  ;;TODO: fulltext search?
  ;; (GET "/api/0.2/companies/index/:key"
  ;;      [key]
  ;;      (get-companies-by-index-key))

  ;;--------------------------------------------------------------------------------------------
  ;;generics
  ;; (GET "api/0.2/:entity-type/:index-name/:key"
  ;;      [entity-type index-name key]
  ;;      (get-entities-by-index-key entity-type index-name key))

(def application (-> (handler/site routes)
                     (wrap-params)
                     reload/wrap-reload
                     (wrap-cors
                      :access-control-allow-origin #".+")))

(defn -main [& args]
  (let [port (Integer/parseInt
               (or (System/getenv "PORT") "8080"))]
    (run-server application {:port port :join? false})
    (info "poc server started on port" port)))

;;scratchpad

;;find all products whose name begins with 'A'
;;note use of predicate functions - datalog queries can execute arbitrary clojure/java code
;;(d/q '[:find ?e ?n ?f :where [?e product/name ?n] [(first ?n) ?f] [(= ?f \A)]] (db conn))
