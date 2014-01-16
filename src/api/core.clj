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

;;(def uri "datomic:mem://localhost:4334/platform")
(def uri "datomic:ddb-local://localhost:8000/platform/poc?aws_access_key_id=[fakeaccesskeyid]&aws_secret_key=[fakesecretkey]")

(def uri "datomic:ddb://us-west-1/platform/poc?aws_access_key_id=AKIAISAUGSDSCIM7WVCA&aws_secret_key=pElW1XrmFWDEH4T21KVtFT37SZohkRVYza7ecXSF")


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

(defn get-entities-bg 
  "get entities by partial match attribute lookup"
  [e a v]
  (let [entities     {"products" "product",
                      "companies" "company",
                      "contacts" "contact",
                      "assets" "asset'"}
        attribute    (keyword (clojure.string/join "/" [(get entities e) a]))
        type         (ffirst (d/q '[:find ?type in $ ?attribute :where 
                                    [?attribute :db/valueType ?t]
                                    [?t :db/ident ?type]] (db conn) attribute))
        ;;cast         (cond ())
        results      (d/q '[:find ?e ?n :in $ ?a ?v :where 
                            [?e ?a  ?n]
                            [(count ?v) ?l]
                            [(count ?n) ?nl]
                            [(>= ?nl ?l)]
                            [(subs ?n 0 ?l) ?f]
                            [(= ?f ?v)]] (db conn) attribute v)]

    (generate-string (map #(.touch (d/entity (db conn) (first %))) results))))

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

(defresource get-entities [e a v]
  :available-media-types ["application/json"]
  :handle-ok (get-entities-bg e a v))

(defroutes routes

  ;;hello world
  (GET "/hello/:name" [name]
       (get-liberator-hello name))
  ;;--------------------------------------------------------------------------------------------

  ;;generic partial match attribute search
  (GET "/api/0.2/:e/:a/:v"
       [e a v]
       
       (get-entities e a v))

  ;;products
  (GET "/api/0.2/products"
       []
       (get-products))

  (GET "/api/0.2/products/:id"
       [id]
       (get-product-by-id id))

  ;;companies
  (GET "/api/0.2/companies"
       []
       (get-companies))

  (GET "/api/0.2/companies/:id"
       [id]
       (get-company-by-id id)))


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
(defn qbyname 
  
  "find product entity matching qstr - index function"
  [qstr]

  (d/q '[:find ?e ?n :in $ ?qstr :where 
         [?e product/name ?n]
         [(count ?qstr) ?l]
         [(count ?n) ?nl]
         [(>= ?nl ?l)]
         [(subs ?n 0 ?l) ?f]
         [(= ?f ?qstr)]] (db conn) qstr))

(d/q '[:find ?e ?n ?f :where 
         [?e product/name ?n] 
         [(first ?n) ?f]
         [(= ?f \A)]] (db conn))
