(ns adapters.models.evaluations-setup
  (:use [datomic.api :only [db q] :as d]))

(def uri "datomic:mem://localhost:4334/platform")

(d/create-database uri)

(def conn (d/connect uri))

;;install partitions
(def partition-schema (read-string(slurp "src/api/models/schema.edn")))

(vec partition-schema)

(d/transact
  conn partition-schema)

(d/q '[:find ?ident
     :where
     [:db.part/db :db.install/partition ?p]
     [?p :db/ident ?ident]
   ] (db conn))

;;install i18n support
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

;;install evaluation entity
(def evaluation-schema
  (read-string(slurp "src/api/models/evaluation-schema.edn")))

(vec evaluation-schema)

(d/transact
  conn evaluation-schema)


;;install evaluation type enums
(def evaluation-types
  (read-string(slurp "src/api/models/evaluation-types.edn")))

(vec evaluation-types)

(d/transact
  conn evaluation-types)

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

;;finally install evaluations
(def evaluation-values
  (read-string(slurp "src/api/models/evaluation-values.edn")))

(vec evaluation-values)

(d/transact
  conn evaluation-values)
