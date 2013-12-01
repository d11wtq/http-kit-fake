# http-kit-fake

A Clojure library for stubbing out calls to the http-kit client in tests.

``` clojure
["http-kit/fake" "0.1.0"]
```

## Usage

Add the following to your dependencies in your project.clj:

``` clojure
[http-kit/fake "0.1.0"]
```

Then use the `with-fake-http` macro to fake some HTTP responses.

``` clojure
(ns your.app
  (:use org.httpkit.fake)
  (:require [org.httpkit.client :as http]))

(with-fake-http {"http://google.com/" "faked"
                 "http://facebook.com/" 500
                 {:url "http://foo.co/" :method :post} {:status 201 :body "ok"}}

  (:body @(http/get "http://google.com/"))      ; "faked"
  (:status @(http/post "http://google.com/"))   ; 200
  (:status @(http/post "http://facebook.com/")) ; 500
  (:status @(http/post "http://foo.co/"))       ; 201
  (:body @(http/post "http://foo.co/"))         ; "ok"
  (http/put "http://foo.co/"))                  ; IllegalArgumentException
```

When a request is sent with http-kit, the list of faked requests is checked.
If any match, based on checking *all* elements in the Map (e.g. :url, :method,
:form-params) then the first match is sent back as a response.

If a request is made to a URL that does not match any of the registered routes,
an IllegalArgumentException is raised and the request is prevented.

For simplicity, you may specify just a URL to match on, and/or just a body or
status code to reply with.

## TODO

I want to refactor this implementation so each match is built as a function
that either returns nil, or processes the request. Then I want to allow
predicates to be used in `with-fake-http` and lambdas to be used in the
responses, thereby allowing for constructs such as:

``` clojure
(with-fake-http {#"^https?://localhost" :allow}
  (http/get "http://localhost/foo")) ; request actually sent
```

## License

Copyright © 2013 Chris Corbyn. See the LICENSE file for details.
