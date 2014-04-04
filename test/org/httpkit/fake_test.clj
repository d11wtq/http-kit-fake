(ns org.httpkit.fake-test
  (:use org.httpkit.fake
        robert.hooke
        clojure.test)
  (:require [org.httpkit.client :as http]))

(deftest fake-test
  (testing "org.httpkit.fake/with-fake-http"
    (testing "with empty routes"
      (with-fake-http []

        (testing "throws IllegalArgumentException on request"
          (is (thrown? IllegalArgumentException
                       (http/get "http://google.com/"))))))

    (testing "with a faked url"
      (testing "using a response string"
        (with-fake-http ["http://google.com/" "faked"]

          (testing "uses the response as the body"
            (is (= "faked"
                   (:body @(http/get "http://google.com/")))))

          (testing "adds a content-type header"
            (is (= "text/html"
                   (:content-type
                     (:headers @(http/get "http://google.com/"))))))

          (testing "uses a 200 response status"
            (is (= 200
                   (:status @(http/get "http://google.com/")))))

          (testing "disallows other urls"
            (is (thrown? IllegalArgumentException
                         (http/get "http://foo.co/"))))))

      (testing "using a response code"
        (with-fake-http ["http://google.com/" 404]

          (testing "uses the response as the status"
            (is (= 404
                   (:status @(http/get "http://google.com/")))))

          (testing "adds a content-type header"
            (is (= "text/html"
                   (:content-type
                     (:headers @(http/get "http://google.com/")))))))))

    (testing "with multiple faked urls"
      (with-fake-http ["http://google.com/" "google"
                       "http://facebook.com/" "facebook"]

        (testing "selects the matching url"
          (is (= "google"
                 (:body @(http/get "http://google.com/"))))
          (is (= "facebook"
                 (:body @(http/get "http://facebook.com/")))))

        (testing "disallows other urls"
          (is (thrown? IllegalArgumentException
                       (http/get "http://foo.co/"))))))

    (testing "without a request method"
      (with-fake-http ["http://google.com/" "faked"]

        (testing "allows all methods"
          (is (= "faked"
                 (:body @(http/get "http://google.com/"))))
          (is (= "faked"
                 (:body @(http/post "http://google.com/"))))
          (is (= "faked"
                 (:body @(http/put "http://google.com/")))))))

    (testing "with a request method"
      (with-fake-http [{:url "http://google.com/" :method :get} "fetched"
                       {:url "http://google.com/" :method :post} "posted"]

        (testing "matches based on the method"
          (is (= "fetched"
                 (:body @(http/get "http://google.com/"))))
          (is (= "posted"
                 (:body @(http/post "http://google.com/")))))

        (testing "disallows other methods"
          (is (thrown? IllegalArgumentException
                       (http/put "http://google.com/"))))))

    (testing "with a regex"
      (with-fake-http [#"^https?://foo\.co/" "ok"]

        (testing "matches according to the regex"
          (is (= "ok"
                 (:body @(http/get "https://foo.co/jim"))))
          (is (= "ok"
                 (:body @(http/get "http://foo.co/bob")))))

        (testing "disallows non-matching urls"
          (is (thrown? IllegalArgumentException
                       (http/get "http://bar.co/"))))))

    (testing "with a function predicate"
      (with-fake-http [#(< (count (:url %)) 20) "short"
                       #(>= (count (:url %)) 20) "long"]

        (testing "tests the predicate"
          (is (= "short"
                 (:body @(http/post "http://a.co/"))))
          (is (= "long"
                 (:body @(http/post "http://not-very-short.com/")))))))

    (testing "with a handler function"
      (testing "returning a map"
        (with-fake-http ["http://google.com/" (fn [orig-fn opts callback]
                                                {:status 418})]

          (testing "returns a future"
            (is (future? (http/get "http://google.com/")))

            (testing "references the map"
              (is (= 418
                     (:status @(http/get "http://google.com/"))))))))

      (testing "returning an integer"
        (with-fake-http ["http://google.com/" (fn [orig-fn opts callback] 418)]

          (testing "returns a future"
            (is (future? (http/get "http://google.com/")))

            (testing "uses the integer as a status"
              (is (= 418
                     (:status @(http/get "http://google.com/"))))))))

      (testing "returning an string"
        (with-fake-http ["http://google.com/" (fn [orig-fn opts callback] "ok")]

          (testing "returns a future"
            (is (future? (http/get "http://google.com/")))

            (testing "uses a 200 status"
              (is (= 200
                     (:status @(http/get "http://google.com/")))))

            (testing "uses the string as a body"
              (is (= "ok"
                     (:body @(http/get "http://google.com/")))))))))


    (testing "with decreasing specificity"
      (with-fake-http [{:url "http://google.com/" :method :post} "posted"
                       #".*" "wildcard"]

        (testing "uses the first match"
          (is (= "posted"
                 (:body @(http/post "http://google.com/"))))
          (is (= "wildcard"
                 (:body @(http/post "http://other.com/")))))))

    (testing "allowing specific urls"
      (with-scope
        (with-fake-http ["http://foo.co/" :allow]

          (add-hook #'org.httpkit.client/request
                    (fn [f opts cb] (future {:received-with opts})))

          (testing "proxies through to #'org.httpkit.client/request"
            (is (= "http://foo.co/"
                   (:url
                     (:received-with @(http/get "http://foo.co/")))))))))

    (testing "denying specific urls"
      (with-fake-http ["http://foo.co/" :deny
                       "http://bar.co/" "ok"]

        (testing "throws IllegalArgumentException on request"
          (is (thrown? IllegalArgumentException
                       (http/get "http://foo.co/"))))

        (testing "allows matched urls"
          (is (= "ok"
                 (:body @(http/get "http://bar.co/")))))))
    
    (testing "http-kit/request works correctly when callback-function is defined"
      (with-fake-http ["http://foo.com/" "ok"]
        (is (= "ok"
               (:body @(http/request {:url "http://foo.com/" :method :get}
                                     (fn [req] req)))))))

    (testing "http-kit/request works correctly without callback function. "
      (with-fake-http ["http://foo.com/" "ok"]
        (is (= "ok"
               (:body @(http/request {:url "http://foo.com/" :method :get}))))))))
