(ns server.core
  (:require
    [aleph.tcp :as tcp]
    [manifold.stream :as s]
    [manifold.deferred :as d]
    [gloss.io :as io]
    [gloss.core :as gloss]
    [clojure.edn :as edn]
    [clojure.string :as string]))

(def protocol
  (gloss/compile-frame
    (gloss/finite-frame :uint32
                        (gloss/string :utf-8))
    pr-str
    edn/read-string))

(defn wrap-duplex-stream [protocol s]
  (let [out (s/stream)]
    (s/connect
      (s/map #(io/encode protocol %) out)
      s)
    (s/splice out
              (io/decode-stream s protocol))))

(defn slow-echo-handler
  [f]
  (fn [s info]
    (d/loop []
      (->
       (d/let-flow [msg (s/take! s ::none)]
                   (when-not (= ::none msg)
                     (d/let-flow [msg'   (d/future (f msg))
                                  result (s/put! s msg')]
                                 (when result
                                   (d/recur)))))
       (d/catch
        (fn [ex]
          (s/put! s (str "ERROR: " ex))
          (s/close! s)))))))

(defn start-server
  "Function borrowed from Aleph TCP example that starts a server connected to duplex stream"
  [handler port]
  (tcp/start-server
   (fn [s info]
     (handler (wrap-duplex-stream protocol s) info))
   {:port port})) ;somehow get this ssl working
