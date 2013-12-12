(ns adapters.models.datomic-setup
  (:use [datomic.api :only [db q] :as d]))

(def uri "datomic:mem://localhost:4334/platform")

(d/create-database uri)

(def conn (d/connect uri))

(defn install-schema [file, conn]
  (d/transact conn (read-string(slurp file))))


(def partition-schema (read-string(slurp "src/api/models/schema.edn")))

(vec partition-schema)

(d/transact
  conn partition-schema)

(d/q '[:find ?ident
     :where
     [:db.part/db :db.install/partition ?p]
     [?p :db/ident ?ident]
   ] (db conn))


;;products
(install-schema "src/api/models/product-schema.edn" conn)

;;contacts
(def contact-schema (read-string(slurp "src/api/models/contact-schema.edn")))

(vec contact-schema)

(d/transact
  conn contact-schema)


;;UOM
(def contact-schema (read-string(slurp "src/api/models/contact-schema.edn")))

(vec uom-schema)

(d/transact
  conn uom-schema)

(d/q '[:find ?e
     :where
     [?e :db/ident :uom.metric/cm]] (db conn))

(d/q '[:find ?id ?ident ?value
       :where
       [?id :db/ident ?ident]
       [(name ?ident) ?value]
       [(namespace ?ident) ?ns]
       [(= ?ns "uom.metric")]] (db conn))


;;taxonomy -----
(def taxonomy-schema (read-string(slurp "src/api/models/taxonomy.edn")))

(vec taxonomy-schema)

(d/transact
  conn taxonomy-schema)

(d/q '[:find ?id ?ident ?value
       :where
       [?id :db/ident ?ident]
       [(name ?ident) ?value]
       [(namespace ?ident) ?ns]
       [(= ?ns "taxonomy.ul")]] (db conn))

(d/q '[:find ?id ?ident ?value
       :where
       [?id :db/ident ?ident]
       [(name ?ident) ?value]
       [(namespace ?ident) ?ns]
       [(= ?ns "taxonomy.external")]] (db conn))


;;i18n ----------
(def i18n-schema (read-string(slurp "src/api/models/i18n.edn")))

(vec i18n-schema)

(d/transact
  conn i18n-schema)


(d/q '[:find ?id ?ident ?value
       :where
       [?id :db/ident ?ident]
       [(name ?ident) ?value]
       [(namespace ?ident) ?ns]
       [(= ?ns "i18n")]] (db conn))

;;category ------
(def category-schema (read-string(slurp "src/api/models/category.edn")))

(vec category-schema)

(d/transact
  conn category-schema)

(d/q '[:find ?id ?ident ?value
       :where
       [?id :db/ident ?ident]
       [(name ?ident) ?value]
       [(namespace ?ident) ?ns]
       [(= ?ns "category")]] (db conn))




;;evaluation types -------

(def evaluation-schema
  (read-string(slurp "src/api/models/evaluation-schema.edn")))

(vec evaluation-schema)

(d/transact
  conn evaluation-schema)


(def evaluation-schema
  (read-string(slurp "src/api/models/evaluation-types.edn")))

(vec evaluation-schema)

(d/transact
  conn evaluation-schema)

(d/q '[:find ?id ?ident ?value
       :where
       [?id :db/ident ?ident]
       [(name ?ident) ?value]
       [(namespace ?ident) ?ns]
       [(= ?ns "claim.ul.ule")]] (db conn))

(d/q '[:find ?id ?ident ?value
       :where
       [?id :db/ident ?ident]
       [(name ?ident) ?value]
       [(namespace ?ident) ?ns]
       [(= ?ns "certification.ul.ule")]] (db conn))


;;source organizations for data - install in enums

(def data-sources-schema
  (read-string(slurp "src/api/models/data-sources.edn")))

(vec data-sources-schema)

(d/transact
  conn data-sources-schema)

(d/q '[:find ?id ?ident ?value
       :where
       [?id :db/ident ?ident]
       [(name ?ident) ?value]
       [(namespace ?ident) ?ns]
       [(= ?ns "organization.ul")]] (db conn))
