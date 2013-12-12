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

;;CACHE ENTITIES IN MEMORY TO AVOID DATABASE ROUNDTRIPS
(def product-basics (select DimProductBasicsExport))

(def product-assets (concat (select DimProductAssetsPhotosExport)
                            (select DimProductAssetsThumbnailsExport)
                            (select DimProductAssetsWebpagesExport)))

(def product-contacts (select DimProductContactsExport))

(def product-certificates (select DimCertificateExport))

(def product-companies (select DimCompanyExport))

;;DATOMIC SETUP
;;TO-DO: move this to own file/lib

;;CONNECTION STRINGS
;;in memory db connection string
(def uri "datomic:mem://localhost:4334/platform")

;;couchbase db connection string
;;(def uri "datomic:couchbase://127.0.0.1/platformCB/test")

;;INITIALIZE

;;wipe out previous database
(d/delete-database uri)
;;create database
(d/create-database uri)

;;CONNECTION
(def conn (d/connect uri))

;;SCHEMATA
;;install schema function
(defn install-schema [file, conn]
  (d/transact conn (read-string(slurp file))))

;;install the base product schema
(install-schema "src/api/models/product-schema.edn" conn)
;;install the asset schema
(install-schema "src/api/models/asset-schema.edn" conn)
;;install the contact schema
(install-schema "src/api/models/contact-schema.edn" conn)
;;install the certificate schema
(install-schema "src/api/models/attribute-set-schema.edn" conn)
;;install the company schema
(install-schema "src/api/models/company-schema.edn" conn)

;;function to generate temp ids for a collection
(defn generate-temp-ids [coll, temp-keyword]
  (map #(into % (clojure.set/rename-keys (dissoc (d/tempid :db.part/user) :part) {:idx temp-keyword})) coll))

(defn tag-uuid-values [coll, key]
  (pmap #(assoc % key (java.util.UUID/fromString (get % key))) coll))

(defn create-product-relationships [acoll, bcoll, join-map, projection]
  (clojure.set/project (clojure.set/join acoll bcoll join-map) projection))

(defn remove-nils [coll]
  (map #(into {} (filter second %)) coll))

(defn prepare-edn [coll, rename-map]
  (map #(clojure.set/rename-keys % rename-map) (remove-nils coll)))

;;DATA
(defn import-products []
  (let [basics      (generate-temp-ids product-basics :product/temp-id)
        assets      (generate-temp-ids product-assets :asset/temp-id)
        contacts    (generate-temp-ids product-contacts :contact/temp-id)
        certs       (generate-temp-ids product-certificates :attribute-set/temp-id)
        ba-rel      (create-product-relationships
                     basics assets {:product/original-id :asset/product-id}
                     [:asset/temp-id :product/temp-id])
        bc-rel      (create-product-relationships
                     basics contacts {:product/original-id :contact/product-id}
                     [:contact/temp-id :product/temp-id])
        bcc-rel     (create-product-relationships
                     basics certs {:product/original-id :attribute-set/product-id}
                     [:attribute-set/temp-id :product/temp-id])
        entity-edn  (prepare-edn (concat basics assets (tag-uuid-values contacts :contact/uuid) (tag-uuid-values certs :attribute-set/uuid))
                                 {:product/temp-id :db/id,
                                  :asset/temp-id :db/id,
                                  :contact/temp-id :db/id,
                                  :attribute-set/temp-id :db/id})
        rel-edn     (prepare-edn (concat ba-rel bc-rel bcc-rel)
                                 {:product/temp-id :db/id,
                                  :asset/temp-id :product/assets,
                                  :contact/temp-id :product/contacts,
                                  :attribute-set/temp-id :product/attribute-sets})
        final-edn   (concat entity-edn rel-edn)
        ]
    ;;(take 10 final-edn)
    (d/transact conn final-edn)))

;;what if we want to add entities to existing database?
(defn import-companies []
   (let [companies  (generate-temp-ids product-companies :db/id)
         co-edn     (prepare-edn (tag-uuid-values companies :company/uuid) {})]
     (d/transact conn co-edn)))

;;now establish a relationship between product and co. -- could be made generic?
(defn create-pc-relationships []
  (let [products    (map #(zipmap [:pid :product/original-id] %)
                         (d/q '[:find ?e ?id  :where [?e product/original-id ?id]] (db conn)))
        companies   (map #(zipmap [:cid :company/product-id] %)
                         (d/q '[:find ?e ?id  :where [?e company/original-id ?id]] (db conn)))
        pc-rel      (create-product-relationships
                     products companies
                     {:product/original-id :company/product-id}
                     [:pid :cid])
        pc-edn      (prepare-edn pc-rel {:pid :db/id :cid :product/brand})]
   (d/transact conn pc-edn)))

;;scratchpad

;;get an entity by id
;;(d/q '[:find ?e :where [?e :product/original-id 25267]] (db conn))

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
