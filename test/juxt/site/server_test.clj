(ns juxt.site.server-test
  (:require [juxt.site.server :as sut]
            [clojure.test :refer [use-fixtures deftest testing is]]))

(use-fixtures
  :each
  (fn [test]
    ;; create a test system to work with and fire it up before running
    ;; the test
    ))

(def sample-holidays
  [{:crux.db/id :our-holiday
    :owner :jms
    :beginning #inst "2020-10-01"
    :end #inst "2020-10-08"
    :description "My holiday to Bournemouth"}

   {:crux.db/id :xmas-and-new-year
    :owner :jms
    :beginning #inst "2020-12-20"
    :end #inst "2020-12-31"
    :description "Winter holidays"}

   {:crux.db/id :october-break
    :owner :cla
    :beginning #inst "2020-10-15"
    :end #inst "2020-10-20"
    :description "Winter holidays"}

   {:crux.db/id :holiday
    :crux.schema/attributes
    {:beginning {:crux.schema/type :crux.schema.type/date}
     :end {:crux.schema/type :crux.schema.type/date}
     :description {:crux.schema/type :crux.schema.type/string
                   :crux.schema/label "Description"}}}])
