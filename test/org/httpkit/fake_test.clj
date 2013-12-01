(ns org.httpkit.fake-test
  (:use org.httpkit.fake
        clojure.test)
  (:require [org.httpkit.client :as http]))

(deftest fake-test
  (testing "org.httpkit.fake/with-fake-http"
    (testing "with empty routes"
      (with-fake-http {}

        (testing "raises IllegalArgumentException on request"
          (is (thrown? IllegalArgumentException
                       (http/get "http://google.com/"))))))

    (testing "with a faked url"
      (testing "using a response string"
        (with-fake-http {"http://google.com/" "faked"}

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
        (with-fake-http {"http://google.com/" 404}

          (testing "uses the response as the status"
            (is (= 404
                   (:status @(http/get "http://google.com/")))))

          (testing "adds a content-type header"
            (is (= "text/html"
                   (:content-type
                     (:headers @(http/get "http://google.com/")))))))))

    (testing "with multiple faked urls"
      (with-fake-http {"http://google.com/" "google"
                       "http://facebook.com/" "facebook"}

        (testing "selects the matching url"
          (is (= "google"
                 (:body @(http/get "http://google.com/"))))
          (is (= "facebook"
                 (:body @(http/get "http://facebook.com/")))))

        (testing "disallows other urls"
          (is (thrown? IllegalArgumentException
                       (http/get "http://foo.co/"))))))

    (testing "without a request method"
      (with-fake-http {"http://google.com/" "faked"}

        (testing "allows all methods"
          (is (= "faked"
                 (:body @(http/get "http://google.com/"))))
          (is (= "faked"
                 (:body @(http/post "http://google.com/"))))
          (is (= "faked"
                 (:body @(http/put "http://google.com/")))))))

    (testing "with a request method"
      (with-fake-http {{:url "http://google.com/" :method :get} "fetched"
                       {:url "http://google.com/" :method :post} "posted"}

        (testing "matches based on the method"
          (is (= "fetched"
                 (:body @(http/get "http://google.com/"))))
          (is (= "posted"
                 (:body @(http/post "http://google.com/")))))

        (testing "disallows other methods"
          (is (thrown? IllegalArgumentException
                       (http/put "http://google.com/"))))))

    (testing "with a regex"
      (with-fake-http {#"^https?://foo\.co/" "ok"}

        (testing "matches according to the regex"
          (is (= "ok"
                 (:body @(http/get "https://foo.co/jim"))))
          (is (= "ok"
                 (:body @(http/get "http://foo.co/bob")))))

        (testing "disallows non-matching urls"
          (is (thrown? IllegalArgumentException
                       (http/get "http://bar.co/"))))))))
