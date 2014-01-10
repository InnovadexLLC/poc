(ns api.models.products
  (:use korma.core
        [korma.db :only (defdb)]
        [datomic.api :only [db q] :as d]))

(def db-spec
  {:classname "com.microsoft.jdbc.sqlserver.SQLServerDriver"
   :subprotocol "sqlserver"
   :user "oli"
   :password "Fibo112358"
   :database "productdata"
   :encrypt true
   :hostNameInCertificate "*.database.windows.net"
   :subname "//qb5yrji2xv.database.windows.net"})

;;ULE DATABASE CONNECTION & RAW ENTITY DATA
(defdb uledb db-spec)

;;DEFINE ENTITIES
(defentity DimProductBasicsExport)
(defentity DimProductAssetsPhotosExport)
(defentity DimProductAssetsThumbnailsExport)
(defentity DimProductAssetsWebpagesExport)
(defentity DimProductContactsExport)
(defentity DimCertificateExport)
(defentity DimCompanyExport)
;;taxonomy elements
(defentity DimCategoryExport)
;;product/taxonomy & taxonomy/taxonomy element relations
(defentity DimCategoryRelationshipsExport)


;;CACHE ENTITIES IN MEMORY TO AVOID DATABASE ROUNDTRIPS
(def product-basics (select DimProductBasicsExport))

(def product-assets (concat (select DimProductAssetsPhotosExport)
                            (select DimProductAssetsThumbnailsExport)
                            (select DimProductAssetsWebpagesExport)))

(def product-contacts (select DimProductContactsExport))

(def product-certificates (select DimCertificateExport))

(def product-companies (select DimCompanyExport))

;;functions to pull categories/subcategories/types out
(def taxonomic-elements (select DimCategoryExport))

(def taxonomy-classes (select DimCategoryRelationshipsExport))
                         
;;DATOMIC SETUP
;;TODO: move this to own file/lib

;;CONNECTION STRINGS
;;in memory db connection string
;;(def uri "datomic:mem://localhost:4334/platform")

;;dev (persistent) transactor
;;(def uri "datomic:dev://localhost:4334/platform")

;;aws ddb-local transactor (persistent)
(def uri "datomic:ddb-local://localhost:8000/platform/poc?aws_access_key_id=[fakeaccesskeyid]&aws_secret_key=[fakesecretkey]")

;;CONNECTION
(def conn (d/connect uri))

;;INITIALIZE

(defn reinitialize-database 
  "wipe out and recreate the database"
  [uri]
  ;;wipe out previous database
  (d/delete-database uri)
  ;;create database
  (d/create-database uri))



;;SCHEMATA
;;install schema utility function
(defn install-schema [file conn]
  (d/transact conn (read-string(slurp file))))


(defn initialize-database-schema []

  (let [schema-files [;;base product schema
                      "src/api/models/product-schema.edn" 
                      ;;asset schema
                      "src/api/models/asset-schema.edn"
                      ;;contact schema
                      "src/api/models/contact-schema.edn"
                      ;;company schema 
                      "src/api/models/company-schema.edn"
                      ;;attribute-sets
                      "src/api/models/attribute-set-schema.edn"
                      ;;taxonomy and taxonomy-elements
                      "src/api/models/taxonomy-schema.edn"
                      ;;i18n
                      "src/api/models/i18n-schema.edn"
                      ]]
    (map #(install-schema % conn) schema-files)))


(defn inject-tempids 
  "generates tempids for each entity in an edn collection"
  [coll]
  (map #(merge {:db/id (d/tempid :db.part/user)} %) coll)) 

(defn inject-squuids 
  "generates squuids for each entity in a collection - every entity should have a squuid"
  [coll squuid-key]
  (map #(merge {squuid-key (d/squuid)} %) coll))

(defn handle-nil-values
  "removes nils from an edn collection prior to transacting with datomic"
  [coll]
  (map #(into {} (filter second %)) coll))


(defn handle-uuid-values
  "tags specified fields/attributes in edn with type #uuid"
  [coll, key]
  (pmap #(assoc % key (java.util.UUID/fromString (get % key))) coll))


(defn create-entities 
  "generic function to import entities to datomic instance"
  [entities uuid-fields]
  
  ;;manipulate the collection
  (let [;;1. generate a temp id for every entity
        id-coll      (inject-tempids entities)
        ;;2. tag specified values as uuids 
        uuid-coll    (if uuid-fields (handle-uuid-values id-coll uuid-fields) id-coll)
        ;;3. remove all nil values
        edn          (handle-nil-values uuid-coll)]
    ;;send the values to datomic as a single transaction
    (d/transact-async conn edn)))


(defn create-relationships [a-join-key b-join-key relation-name]
  (let [joined-set (d/q '[:find ?ae ?be :in $ ?a-join-key ?b-join-key 
                         :where [?ae ?a-join-key ?j]  [?be ?b-join-key ?j]] 
                         (db conn) a-join-key b-join-key)
       edn        (map #(zipmap [:db/id relation-name] %) joined-set)]

    (d/transact-async conn edn)))





(defn import-data
  "script to import entities and create relationships"
  []
  ;;create entities
  (create-entities product-basics nil)
  (create-entities product-assets nil)
  (create-entities product-companies :company/uuid)
  (create-entities product-contacts :contact/uuid)
  (create-entities product-certificates :attribute-set/uuid)

  (create-entities taxonomic-elements nil)
  (println "entities created")

  ;;create relationships

  ;;product/asset relationship
  (create-relationships :product/original-id :asset/product-id :product/assets)
  ;;product/company relationship
  (create-relationships :product/original-id :company/product-id :product/brand)
  ;;product/contact relationship
  (create-relationships :product/original-id :contact/product-id :product/contacts)
  ;;product/certification attribute-set relationship
  (create-relationships :product/original-id :attribute-set/product-id :product/attribute-sets)

  (println "relationships created"))


(defn import-taxonomies [coll]
  (let [coll (distinct (map #(dissoc % :taxonomy/elements 
                                     :taxonomic-element/type 
                                     :taxonomic-element/id
                                     :product/original-id) coll))]
    (create-entities coll nil)))

(defn create-taxonomy-relationships [coll]
      (let [;; taxonomy->element     (map #(d/q '[:find ?e :in $ ?type ?id 
            ;;                                    :where 
            ;;                                    [?e :taxonomic-element/type ?type] 
            ;;                                    [?e :taxonomic-element/id ?id]] 
            ;;                                  (db conn) 
            ;;                                  (:taxonomic-element/type %) 
            ;;                                  (:taxonomic-element/id %)) coll)

            taxonomy->element     (distinct (map #(dissoc % :product/original-id) coll))

            taxonomy->element     (map #(d/q '[:find ?t ?e :in $ ?type ?eid ?tid
                                               :where 
                                               [?e :taxonomic-element/type ?type] 
                                               [?e :taxonomic-element/id ?eid]
                                               [?t :taxonomy/id ?tid]] 
                                             (db conn) 
                                             (:taxonomic-element/type %) 
                                             (:taxonomic-element/id %)
                                             (:taxonomy/id %)) taxonomy->element)

            taxonomy->element     (map #(zipmap [:db/id :taxonomy/elements] (first %)) taxonomy->element)

            product->taxonomy     (map #(dissoc % :taxonomy-element/id 
                                                  :taxonomy-element/type) coll)

            product->taxonomy     (map #(d/q '[:find ?p ?t :in $ ?pid ?tid
                                               :where
                                               [?p :product/original-id ?pid]
                                               [?t :taxonomy/id ?tid]]
                                             (db conn)
                                             (:product/original-id %)
                                             (:taxonomy/id %)) product->taxonomy)
            product->taxonomy     (map #(zipmap [:db/id :product/taxonomies] (first %)) product->taxonomy)


            ;; taxonomy->element     (map #(merge {:taxonomy/elements %1} %2) taxonomy->element coll) 
            ;; taxonomy->element     (distinct (map #(dissoc % :product/original-id) taxonomy->element))
            ;; taxonomy->element     (group-by first taxonomy->element)
            ]
        ;; (create-entities taxonomy->element)
        taxonomy->element       
        
        ;; product->taxonomy
        ))

    ;;----------------------------------------------------------------------------------------------

    ;;scratchpad

    ;;get an entity by id
    ;;(d/q '[:find ?e :where [?e :product/original-id 25267]] (db conn)))

;;reify an entity
;; (.touch (d/entity (db conn) (ffirst (d/q '[:find ?e :where [?e :product/original-id 25267]] (db conn)))))

;;how many contacts in the database?
;; (let [entity-ids        (d/q '[:find ?e :where [?e :attribute-set/uuid]] (db conn))
;;       entity-count      (count entity-ids)
;;       entity-reified    (map #(.touch (d/entity (db conn) (first %))) entity-ids)
;;       ]

;;   entity-reified)

;;query to retrieve all entity ids and their assoc. ule product ids
;;(d/q '[:find ?e ?oid :where [?e product/original-id ?oid] [?e product/original-id]] (db conn))

;;transact entities immediately - decided against this in favour of building all rels on tempids
;;generate transaction data and temp ids for products basics
;(d/transact conn product-basics-edn)

;;generate transaction data and temp ids for product assets
;(d/transact conn (generate-temp-ids product-assets))

;;generate transaction data and temp ids for product contacts
;(d/transact conn (generate-temp-ids product-contacts))


;; (defn build-relationships []
;;    (let [products
;;          (d/q '[:find ?e ?oid :where
;;                 [?e product/original-id ?oid]
;;                 [?e product/original-id]] db conn))]
;;       products)
