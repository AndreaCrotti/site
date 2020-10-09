(ns juxt.site.server
  (:require
   [clojure.java.io :as io]
   [crux.api :as crux]
   [hiccup.page :refer [html5]]
   [integrant.core :as ig]
   [juxt.reap.alpha.decoders :as reap]
   [juxt.spin.alpha.handler :as spin.handler]
   [juxt.spin.alpha.resource :as spin.resource]
   [juxt.spin.alpha.server :as spin.server]
   [juxt.vext.content-store :as cstore]
   juxt.vext.ring-server))

(defmethod ig/init-key ::content-store [_ {:keys [vertx dir]}]
  (.mkdirs (io/file dir))
  (cstore/->VertxFileContentStore vertx (io/file dir) (io/file dir)))

(defn schema->form
  "Generate an HTML5 form from Crux attributes"
  [attributes]
  (html5
   [:form {:method "POST" :enctype "multipart/form-data"}
    (into
     [:field-set]
     (for [[att-k {:crux.schema/keys [_ label]}] attributes
           :let [n (name att-k)]]
       [:div
        (when label [:label {:for n} label])
        [:input {:name n :type "text"}]]))
    [:input {:type "submit" :value "Submit"}]]))

(defn handler [{:keys [crux content-store]}]
  (assert crux)
  (spin.handler/handler
   (reify
     spin.resource/ResourceLocator
     (locate-resource [_ uri request]
       (let [db (crux/db crux)]
         (when-let [eid (ffirst
                         (crux/q db {:find ['?e]
                                     :where [['?e :juxt.site/url uri]]}))]
           (crux/entity db eid))))

     spin.resource/GET
     (get-or-head [_ server resource response request respond raise]
       (cond
         ;; If there's some content, send it over
         (:juxt.vext.content-store/file resource)
         (respond
          {:status 200
           :body (io/file (:juxt.vext.content-store/file resource))})

         :else
         ;; If the resource represents a relation, reply with a form
         ;; representing that relation:
         (respond
          {:status 200
           :headers {"content-type" "text/html;charset=utf8"}
           :body
           (schema->form (:crux.schema/attributes resource))})))

     spin.resource/POST
     (post [_ server resource response request respond raise]
       (let [vtxreq (:juxt.vext/request request)]
         (case ((juxt :juxt.http/type :juxt.http/subtype) (reap/content-type (get-in request [:headers "content-type"])))
           ["application" "json"]
           (.bodyHandler
            vtxreq
            (reify io.vertx.core.Handler
              (handle [_ buffer]
                (respond
                 {:status 200
                  :headers {"content-type" "text/plain;charset=utf8"}
                  :body "Thanks! Buon Viaggio!\n"}))))

           ["multipart" "form-data"]
           (do
             (.setExpectMultipart vtxreq true)
             (.endHandler
              vtxreq
              (reify io.vertx.core.Handler
                (handle [_ _]
                  (let [data (into {} (.formAttributes vtxreq))
                        eid (java.util.UUID/randomUUID)
                        content-location (str (:juxt.site/url resource) "/" eid)
                        e (into {:crux.db/id eid
                                 :juxt.site/url content-location}
                                (map (fn [[k v]] [(keyword k) v]) data))]

                    (crux/submit-tx
                     crux
                     [[:crux.tx/put e]])

                    (crux/sync crux)

                    (respond
                     {:status 200
                      :headers
                      {"content-type" "text/html;charset=utf8"
                       "content-location" content-location}
                      :body (format "<h1>Thanks</h1><p>Your holiday can be edited <a href='%s'>here</a>.</p>" content-location)})))))))))

     spin.resource/PUT
     (put [_ server resource response request respond raise]
       (let [ct (reap/content-type (get-in request [:headers "content-type"]))]
         (case (:juxt.http/type ct)
           ("image" "video") ;; Allow upload of static image content
           (->
            (cstore/post-content content-store (.toFlowable (:juxt.vext/request request)))
            (.subscribe
             (reify io.reactivex.functions.Consumer ;; happy path!
               (accept [_ {:keys [k file]}]
                 (let [id (java.util.UUID/randomUUID)]
                   (crux/submit-tx
                    crux
                    [[:crux.tx/put
                      {:crux.db/id id
                       :juxt.site/url (juxt.spin.alpha.handler/request-url request)
                       :juxt.vext.content-store/k k
                       :juxt.vext.content-store/file (.getAbsolutePath file)}]])
                   (crux/sync crux))
                 (respond
                  (merge
                   response
                   {:body (format "Thanks, that looks like a wonderful %s!\n" (:juxt.http/type ct))}))))
             (reify io.reactivex.functions.Consumer ;; sad path!
               (accept [_ t]
                 (raise t)))))))))

   (reify
     spin.server/ServerOptions
     (server-header [_] "JUXT Site!!")
     (server-options [_]))))
