(ns org.httpkit.fake
  "Library to fake HTTP traffic with org.http-kit.client."
  (:use [robert.hooke :only [with-scope add-hook]])
  (:require org.httpkit.client))

(defn regex?
  [obj]
  (instance? java.util.regex.Pattern obj))

(defn handle-unmatched
  [orig-fn req callback]
  (throw (IllegalArgumentException.
           (str "Attempted to perform "
                (.toUpperCase (name (req :method)))
                " on unregistered URL "
                (req :url)
                " and real HTTP requests are disabled."))))

(defn response-map
  [opts res-spec]
  (merge
    {:opts opts
     :status 200
     :headers {:content-type "text/html"
               :server "org.htpkit.fake"}}
    (cond
      (string? res-spec) {:body res-spec}
      (number? res-spec) {:status res-spec}
      :else res-spec)))

(defn responder
  [res-spec]
  (if (fn? res-spec)
    res-spec
    (fn [orig-fn opts callback]
      (if (= :allow res-spec)
        (orig-fn opts callback)
        (future ((or callback identity)
                 (response-map opts res-spec)))))))

(defn matches?
  [req-spec sent-opts]
  (every? (fn [[k v]]
            (if (regex? v)
              (re-find v (sent-opts k))
              (= v (sent-opts k))))
          req-spec))

(defn predicate
  [req-spec]
  (if (fn? req-spec)
    req-spec
    (let [to-match (if (map? req-spec) req-spec {:url req-spec})]
      #(matches? to-match %))))

(defn handler
  [req res]
  (let [handled? (predicate req)
        responder (responder res)]
    (fn [orig-fn opts callback]
      (if (handled? opts)
        (responder orig-fn opts callback)))))

(defn build-handlers
  [spec]
  (concat
    (map #(apply handler %) spec)
    [handle-unmatched]))

(defn stub-request
  [spec]
  (let [handlers (build-handlers spec)]
    (fn [orig-fn opts callback]
      (first (keep identity (map #(% orig-fn opts callback) handlers))))))

(defmacro with-fake-http
  "Define a series of routes to be faked in matching calls to
  org.httpkit.client/request.

  The spec argument is a Map whose keys contain a predicate for the request and
  whose values are the responses to be returned.

  The predicate keys in the Map may take one of the following forms:

    1. A function accepting a Map (request opts) and returning true on a match.
    2. A String, which must be an exact match on the URL.
    3. A Regex, which must match on the URL.
    4. A Map, whose keys and values must match the same keys and values in the
       request. Values may be specified as Regexes.

  The responses in the Map may take one of the following forms:

    1. A function accepting the actual (unstubbed) #'org.httpkit.client/request
       fn, the request opts Map and a callback function as arguments. This must
       return a promise or a future, which when dereferenced returns a Map.
    2. A String, which is then used as the :body of the response.
    3. An Integer, which is then used as the :status of the response.
    4. A Map, which is used as the actual response Map, merged with some
       defaults.
    5. The keyword :allow, which whitelists this request and allows the real
       connection.
    6. The keyword :deny, which blacklists this request and throws an
       IllegalArgumentException if it is attempted.

  Each predicate is tested in the order in which they are specified. As soon as
  the first predicate matches, the response is invoked. If none of the
  predicates match, an IllegalArgumentException is thrown and the request is
  disallowed.
  "
  [spec & body]
  `(with-scope
      (add-hook #'org.httpkit.client/request
                (stub-request ~spec))
      ~@body))
