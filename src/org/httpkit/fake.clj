(ns org.httpkit.fake
  "Library to fake HTTP traffic with org.httpkit.client."
  (:use [robert.hooke :only [with-scope add-hook]])
  (:require org.httpkit.client))

(defn regex?
  [obj]
  (instance? java.util.regex.Pattern obj))

(defn deref?
  [obj]
  (instance? clojure.lang.IDeref obj))

(defn handle-unmatched
  "The default handler function that is applied in the case a request sent to
  #'org.httpkit.client/request is not matched by any other handlers.

  This function simply throws an IllegalArgumentException."
  [orig-fn req callback]
  (throw (IllegalArgumentException.
           (str "Attempted to perform "
                (.toUpperCase (name (req :method)))
                " on unregistered URL "
                (req :url)
                " and real HTTP requests are disabled."))))

(defn response-map
  "Build the response data based on the request data and the spec used in
  `with-fake-http`.

  This function merges in some defaults."
  [opts res-spec]
  (merge
    {:opts opts
     :status 200
     :headers {:content-type "text/html"
               :server "org.httpkit.fake"}}
    (cond
      (string? res-spec) {:body res-spec}
      (number? res-spec) {:status res-spec}
      :else res-spec)))

(defn responder-wrapper
  "Wraps the provided responder to add missing boilerplate code."
  [responder]
  (fn [orig-fn opts callback]
    (let [res (responder orig-fn opts callback)]
      (if (deref? res)
        res
        (future ((or callback identity)
                 (response-map opts res)))))))

(defn responder
  "Returns a lambda that is applied in order to provide a response to a matched
  request.

  The lambda returned by this function does not check if the request was
  matched or not; it simply returns a response."
  [res-spec]
  (responder-wrapper
    (if (fn? res-spec)
      res-spec
      (fn [orig-fn opts callback]
        (case res-spec
          :allow (orig-fn opts callback)
          :deny (handle-unmatched orig-fn opts callback)
          res-spec)))))

(defn matches?
  "Compares a request spec from `with-fake-http` against an actual request sent
  to #'org.httpkit.client/request and returns true if they match."
  [req-spec sent-opts]
  (every? (fn [[k v]]
            (if (regex? v)
              (re-find v (sent-opts k))
              (= v (sent-opts k))))
          req-spec))

(defn predicate
  "Returns a lambda used to test if a request sent to
  #'org.httpkit.client/request matches the spec in `with-fake-http`."
  [req-spec]
  (if (fn? req-spec)
    req-spec
    (let [to-match (if (map? req-spec) req-spec {:url req-spec})]
      #(matches? to-match %))))

(defn handler
  "Returns a lambda that either handles a request sent to
  #'org.httpkit.client/request, or returns nil.

  This is simply a combination of the predicate and the responder, wrapped in
  a lambda."
  [req res]
  (let [handled? (predicate req)
        responder (responder res)]
    (fn [orig-fn opts callback]
      (if (handled? opts)
        (responder orig-fn opts callback)))))

(defn build-handlers
  "Converts the spec from `with-fake-http` into a list of handler functions.

  During a request sent to #'org.httpkit.client/request, each handler function
  will be applied in order until one returns non-nil, otherwise
  `handle-unmatched` is applied, as it is the last handler function in this
  list."
  [spec]
  (if (= 0 (rem (count spec) 2))
    (concat
      (map #(apply handler %) (partition 2 spec))
      [handle-unmatched])
    (throw (IllegalArgumentException.
             "Mismatched let forms in #'org.httpkit.fake/with-fake-http"))))

(defn stub-request
  "Returns a function that provides a hook to #'org.httpkit.client/request."
  [spec]
  (let [handlers (build-handlers spec)]
    (fn [orig-fn opts callback]
      (->> handlers
           (map #(% orig-fn opts callback))
           (keep identity)
           (first)))))

(defmacro with-fake-http
  "Define a series of routes to be faked in matching calls to
  #'org.httpkit.client/request.

  The spec argument is a vector of key-value pairs—as in a let binding form—in
  which the keys contain a predicate for the request and the values are the
  responses to be returned.

  The predicates may take one of the following forms:

    1. A function accepting a Map (request opts) and returning true on a match.
    2. A String, which must be an exact match on the URL.
    3. A Regex, which must match on the URL.
    4. A Map, whose keys and values must match the same keys and values in the
       request. Values may be specified as Regexes.

  The responses may take one of the following forms:

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
