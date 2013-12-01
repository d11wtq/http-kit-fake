(ns org.httpkit.fake
  "Macros to fake HTTP traffic with org.http-kit.client."
  (:use [robert.hooke :only [with-scope add-hook]])
  (:require org.httpkit.client))

;; FIXME: Refactor to use strategy pattern

(defn- handle-unmatched
  [req]
  (throw (IllegalArgumentException.
           (str "Attempted to perform "
                (.toUpperCase (name (req :method)))
                " on unregistered URL "
                (req :url)
                " and real HTTP requests are disabled."))))

(defn- matches?
  [fake sent]
  (every? (fn [[k v]] (= (sent k) v)) fake))

(defn- normalize-request
  [req]
  (if (map? req)
    req
    {:url req}))

(defn- normalize-response
  [res]
  (cond
    (map? res) (merge {:status 200
                       :headers {:content-type "text/html"
                                 :server "faked"}}
                      res)
    (string? res) (recur {:body res})
    (integer? res) (recur {:status res})))

(defn- normalize-routes
  [routes]
  (reduce (fn [acc [k v]]
            (merge acc {(normalize-request k) (normalize-response v)}))
          {}
          routes))

(defn fake-request
  [routes]
  (let [routes (normalize-routes routes)]
    (fn [f req callback]
      (let [pred? #(matches? % req)
            match (first (filter pred? (keys routes)))]
        (if match
          (future ((or callback identity)
                   (merge req (routes match))))
          (handle-unmatched req))))))

(defmacro with-fake-http
  "Define a series of routes to be faked in matching calls to
  org.httpkit.client/request.

  The routes argument is a Map whose keys contain a subset of the request that
  must be matched and whose values are the responses to be returned. If the
  key is a String, it will be used as a match on the :url alone.

  If found, the first matching fake request is used to return a response,
  otherwise an IllegalArgumentException is thrown and the request is
  disallowed."
  [routes & body]
  `(with-scope
      (add-hook #'org.httpkit.client/request
                (fake-request ~routes))
      ~@body))
