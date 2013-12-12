(ns api.models.db
  (:use korma.core
        [korma.db :only (defdb)]
        [datomic.api :only [db q] :as d])
  (:require [api.models.ule-connection :as ule]))

;;DATABASE CONNECTIONS
;;TODO: migrate all this to a let form inside a defn?

;;connection to ULE database
(defdb uledb ule/db-spec)

;;connection to datomic database (UL Platform)
(def uri "datomic:mem://localhost:4334/platform")
(def conn (d/connect uri))
(def p-db (d/db conn))
;;ADAPTERS

;;CATEGORY ADAPTER

;;get a handle on the category table
(defentity DimCategory)

;;functions to pull categories/subcategories/types out
(defn get-categories []
 (select DimCategory
         (modifier "distinct")
         (fields :category)))

(defn get-subcategories []
  (select DimCategory
          (modifier "distinct")
          (fields :category :subcategory)
          (where {:subcategory [not= nil], :category [not= nil]})))

(defn get-types []
  (select DimCategory
          (modifier "distinct")
          (fields :subcategory :type)
          (where {:subcategory [not= nil], :type [not= nil]})))

;;TODO - refactor let bindings to function calls?
(defn generate-tempids [coll]
  )

;;categories are defined by a taxonomy, a parent, a name, an optional label
(defn generate-edn []

  (let [t-map       {:category/taxonomy :taxonomy.ul/ule}

        ;;first create the categories and record the entity ids
        c-initial   (pmap #(into % (d/tempid :db.part/categories)) (get-categories))
        c-lookup    (zipmap (pmap #(get % :category) c-initial)
                            (pmap #(get % :idx) c-initial))
        c-cleaned   (pmap #(apply dissoc % [:part]) c-initial)
        c-renamed   (clojure.set/rename c-cleaned {:category :category/name, :idx :db/id})
        c-decorated (pmap #(conj t-map %) c-renamed)
        c-final     c-decorated

        ;;next get all the subcategories for a category and create entities for them
        ;;use the entity ids from the last pass to set the parent attribute
        ;;record the subcategory ids for use in the next step

        s-initial   (pmap #(into % (d/tempid :db.part/categories)) (get-subcategories))
        s-lookup    (zipmap (pmap #(get % :subcategory) s-initial)
                            (pmap #(get % :idx) s-initial))
        s-parents   (pmap #(update-in % [:category] c-lookup)  s-initial)
        s-cleaned   (into [] (pmap #(apply dissoc % [:part]) s-parents))
        s-renamed   (clojure.set/rename s-cleaned {:category :category/parent,
                                                   :subcategory :category/name,
                                                   :idx :db/id})
        s-decorated (pmap #(conj {:category/taxonomy :taxonomy.ul/ule} %) s-renamed)
        s-final    s-decorated

        ;;finally get all the types for a subcategory and create entities for them
        ;;use the subcategory entity ids to set the parent attribute
        t-initial   (pmap #(into % (d/tempid :db.part/categories)) (get-types))
        t-parents   (pmap #(update-in % [:subcategory] s-lookup)  t-initial)
        t-cleaned   (into [] (pmap #(apply dissoc % [:part]) t-parents))
        t-renamed   (clojure.set/rename t-cleaned {:subcategory :category/parent,
                                                   :type :category/name,
                                                   :idx :db/id})
        t-decorated (pmap #(conj {:category/taxonomy :taxonomy.ul/ule} %) t-renamed)
        t-final     t-decorated

        test        (c-lookup "Window Treatments")]

   (into [] (concat t-final s-final c-final))))

(def edn (generate-edn))

(defn transact-categories []
  (d/transact conn edn))

;;SAMPLE QUERY

;; (d/q '[:find ?e, ?p, ?pname
;;        :in $ ?name
;;        :where
;;        [?e :category/name ?name]
;;        [?e :category/parent ?p]
;;        [?p :category/name ?pname]] p-db "Sealer")


;;BEGIN EVALUATIONS ADAPTER

;;get a handle on the evalations view
(defentity DimEvaluationExport)

(def evaluations (select DimEvaluationExport))

;;straightforward import of evaluation types goes here



;;BEGIN CERTIFICATION AND CLAIMS ADAPTER
(defentity CertificationClaimsExportView)

;;wrap in a def to avoid nrepl/emacs io costs
(def certifications&claims (select CertificationClaimsExportView))

;;straightforward import of cerfification data goes here
