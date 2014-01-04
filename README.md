# http-kit-fake

A Clojure library for stubbing out calls to the http-kit client in tests.

[![Build Status](https://secure.travis-ci.org/d11wtq/http-kit-fake.png?branch=master)](http://travis-ci.org/d11wtq/http-kit-fake)

``` clojure
[http-kit.fake "0.2.1"]
```

## Usage

Use the `with-fake-http` macro to fake some HTTP responses.

``` clojure
(ns your.app
  (:use org.httpkit.fake)
  (:require [org.httpkit.client :as http]))

;; denying all http-traffic
(with-fake-http []
  (http/get "http://google.com/")) ; IllegalArgumentException (blocked)

;; simple requests and responses
(with-fake-http ["http://google.com/" "faked"
                 "http://flickr.com/" 500]
  (:body @(http/get "http://google.com/"))    ; "faked"
  (:status @(http/get "http://flickr.com/"))) ; 500

;; matching a specific request method
(with-fake-http [{:url "http://foo.co/" :method :post} {:status 201 :body "ok"}]
  (:status @(http/post "http://foo.co/")) ; 201
  (http/get  "http://foo.co/"))           ; IllegalArgumentException (blocked)

;; using a regex on the URL
(with-fake-http [#"^https?://google.com/" "ok"]
  (:body @(http/get "https://google.com/foo")) ; "ok"
  (:body @(http/get "http://google.com/bar"))) ; "ok"

;; allowing traffic on some URLs
(with-fake-http [#"https?://localhost/" :allow]
  (http/get "http://localhost/foo")) ; <<some real response>> (:allow)

;; explicitly denying traffic
(with-fake-http [#"^http://localhost/unsafe" :deny
                 #"^http://localhost/" :allow]
  (http/get "http://localhost/foo")     ; <<some real response>> (:allow)
  (http/get "http://localhost/unsafe")) ; IllegalArgumentException (:deny)
```

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

  1. A function accepting the actual (unstubbed) `#'org.httpkit.client/request`
     fn, the request opts Map and a callback function as arguments.
  2. A String, which is then used as the `:body` of the response.
  3. An Integer, which is then used as the `:status` of the response.
  4. A Map, which is used as the actual response Map, merged with some
     defaults.
  5. The keyword `:allow`, which whitelists this request and allows the real
     connection.
  6. The keyword `:deny`, which blacklists this request and throws an
     `IllegalArgumentException` if it is attempted.

Each predicate is tested in the order in which they are specified. As soon as
the first predicate matches, the response is invoked. If none of the
predicates match, an `IllegalArgumentException` is thrown and the request is
disallowed.

## Advanced Usage

Internally the request predicates in the examples above are converted to
function predicates. Likewise, the responses in the examples above are
converted to handler functions. You may specify your own functions if you need
to do advanced matching or handling of the request.

``` clojure
(with-fake-http [#(< (count (% :url)) 20) (fn [orig-fn opts callback]
                                            {:status 418})]
  (:status @(http/get "http://a.co/"))) ; 418
```

If your callback returns a future (or any instance of `clojure.lang.IDeref`, no
special treatment will be done on the return value and it is assumed your
handler invokes the callback when needed. In all other cases, http-kit-fake
completes the response as normal and additionally adds the necessary
boilerplate to invoke the callback argument.

An implementation returning a future should look like this:

``` clojure
(with-fake-http [#(< (count (% :url)) 20) (fn [orig-fn opts callback]
                                            (future ((or callback identity)
                                                     {:status 418})))]
  (:status @(http/get "http://a.co/"))) ; 418
```

If you want to pass the call along to http-kit, such as in the case of
`:allow`, you can apply `(orig-fn opts callback)`, since the first argument is
the unstubbed `#'org.httpkit.client/request` function.

## License

Copyright © 2013 Chris Corbyn. See the LICENSE file for details.
