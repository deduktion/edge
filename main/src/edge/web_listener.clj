;; Copyright © 2016, JUXT LTD.

(ns edge.web-listener
  (:require
   [bidi.bidi :refer [tag]]
   [bidi.vhosts :refer [make-handler vhosts-model]]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   [cheshire.core :as json]
   edge.yada.lacinia
   [edge.examples :refer [authentication-example-routes]]
   [edge.hello :refer [hello-routes other-hello-routes]]
   [hiccup.core :refer [html]]
   [integrant.core :as ig]
   [ring.util.mime-type :refer [ext-mime-type]]
   [schema.core :as s]
   [selmer.parser :as selmer]
   [yada.resources.resources-resource :refer [new-resources-resource]]
   [yada.resources.webjar-resource :refer [new-webjar-resource]]
   [yada.yada :refer [handler resource] :as yada]))

(defn content-routes []
  ["/"
   [
    ["" (assoc (new-resources-resource "public/") :id :static)]]])

(defn routes
  "Create the URI route structure for our application."
  [config]
  [""
   [
    ;; Document routes
    ["/" (yada/redirect (:edge.web-listener/index config))]

    ["" (:edge.web-listener/routes config)]

    ;; Hello World!
    (hello-routes)
    (other-hello-routes)

    (authentication-example-routes)

    ["/api" (-> (hello-routes)
                ;; Wrap this route structure in a Swagger
                ;; wrapper. This introspects the data model and
                ;; provides a swagger.json file, used by Swagger UI
                ;; and other tools.
                (yada/swaggered
                  {:info {:title "Hello World!"
                          :version "1.0"
                          :description "An API on the classic example"}
                   :basePath "/api"})
                ;; Tag it so we can create an href to this API
                (tag :edge.resources/api))]

    ;; Swagger UI
    ;; TODO: extract to module?
    ["/swagger" (-> (new-webjar-resource "/swagger-ui" {:index-files ["index.html"]})
                    ;; Tag it so we can create an href to the Swagger UI
                    (tag ::swagger))]

    ;;(graphql/routes config)

    ;; Our content routes, and potentially other routes.
    (content-routes)

    ;; This is a backstop. Always produce a 404 if we go there. This
    ;; ensures we never pass nil back to Aleph.
    [true (handler nil)]]])

(defmethod ig/init-key :edge/web-listener
  [_ {:edge.web-listener/keys [vhost port] :as config}]
  (let [vhosts-model (vhosts-model [vhost (routes config)])
        listener (yada/listener vhosts-model {:port port})]
    (log/infof "Started HTTP listener on port %s" (:port listener))
    {:listener listener
     ;; Retaining config helps debugging, and console 'annoucement' in dev
     :config (select-keys config [:edge.web-listener/vhost :edge.web-listener/port])}))

(defmethod ig/halt-key! :edge/web-listener [_ {:keys [listener]}]
  (when-let [close (:close listener)]
    (close)))
