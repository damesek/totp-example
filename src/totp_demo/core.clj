(ns totp-demo.core
  (:require [reitit.ring :as ring]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [hiccup.core :refer [html]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [one-time.core :as ot]
            [one-time.qrgen :as qrgen])
  (:import [java.util Base64]))

(def data-file "data.edn")

(defn load-users []
  (if (.exists (io/file data-file))
    (let [content (slurp data-file)]
      (if (str/blank? content)
        {}
        (edn/read-string content)))
    {}))

(defn save-users [users-map]
  (spit data-file (pr-str users-map)))

(defn html-response [hiccup-content]
  {:status 200
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body (html hiccup-content)})

(defn login-page [error-msg]
  (html-response
   [:html
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:title "Login"]
     [:script {:src "https://cdn.tailwindcss.com"}]]
    [:body.bg-gray-100.min-h-screen.flex.items-center.justify-center
     [:div.bg-white.shadow-md.rounded-lg.p-8.w-full.max-w-md
      [:h1.text-2xl.font-bold.mb-6 "Login"]
      (when error-msg
        [:div.bg-red-100.border.border-red-400.text-red-700.px-4.py-3.rounded.mb-4 error-msg])
      [:form {:method "post" :action "/login" :class "space-y-4"}
       [:div
        [:label.block.text-gray-700 "Username:"]
        [:input {:type "text" :name "username" :class "mt-1 block w-full border border-gray-300 rounded-md p-2"}]]
       [:div
        [:label.block.text-gray-700 "6-digit code:"]
        [:input {:type "text" :name "token" :class "mt-1 block w-full border border-gray-300 rounded-md p-2"}]]
       [:button {:type "submit" :class "w-full bg-blue-500 hover:bg-blue-600 text-white font-bold py-2 px-4 rounded"} "Login"]]]]]))

(defn registration-page [error-msg]
  (html-response
   [:html
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:title "Registration"]
     [:script {:src "https://cdn.tailwindcss.com"}]]
    [:body.bg-gray-100.min-h-screen.flex.items-center.justify-center
     [:div.bg-white.shadow-md.rounded-lg.p-8.w-full.max-w-md
      [:h1.text-2xl.font-bold.mb-6 "Registration"]
      (when error-msg
        [:div.bg-red-100.border.border-red-400.text-red-700.px-4.py-3.rounded.mb-4 error-msg])
      [:form {:method "post" :action "/register" :class "space-y-4"}
       [:div
        [:label.block.text-gray-700 "Username:"]
        [:input {:type "text" :name "username" :class "mt-1 block w-full border border-gray-300 rounded-md p-2"}]]
       [:button {:type "submit" :class "w-full bg-green-500 hover:bg-green-600 text-white font-bold py-2 px-4 rounded"} "Register"]]]]]))

(defn protected-page [username]
  (html-response
   [:html
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:title "Protected Content"]
     [:script {:src "https://cdn.tailwindcss.com"}]]
    [:body.bg-gray-100.min-h-screen.flex.items-center.justify-center
     [:div.bg-white.shadow-md.rounded-lg.p-8.w-full.max-w-md.text-center
      [:h1.text-2xl.font-bold.mb-4 "Protected Content"]
      [:p.mb-4 "Successfully logged in, " [:b username] "! This is protected content viewable only after a successful login."]
      [:a {:href "/" :class "inline-block bg-red-500 hover:bg-red-600 text-white font-bold py-2 px-4 rounded"} "Logout"]]]]))

(def app-routes
  (ring/router
   [["/"
     {:get (fn [_]
             (html-response
              [:html
               [:head
                [:meta {:charset "utf-8"}]
                [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
                [:title "TOTP Demo"]
                [:script {:src "https://cdn.tailwindcss.com"}]]
               [:body.bg-gray-100.min-h-screen.flex.items-center.justify-center
                [:div.bg-white.shadow-md.rounded-lg.p-8.w-full.max-w-md.text-center
                 [:h1.text-2xl.font-bold.mb-4 "TOTP Two-Factor Authentication Demo"]
                 [:div.space-x-4
                  [:a {:href "/register" :class "inline-block bg-green-500 hover:bg-green-600 text-white font-bold py-2 px-4 rounded"} "Registration"]
                  [:a {:href "/login" :class "inline-block bg-blue-500 hover:bg-blue-600 text-white font-bold py-2 px-4 rounded"} "Login"]]]]]))}]
    ["/register"
     {:get (fn [_]
             (registration-page nil))
      :post (fn [req]
              (let [username (-> req :params :username str/trim)]
                (cond
                  (str/blank? username)
                  (registration-page "Username cannot be empty.")
                  :else
                  (let [users (load-users)
                        existing? (get users username)
                        secret (ot/generate-secret-key)
                        users' (assoc users username {:secret secret})]
                    (save-users users')
                    (let [baos (qrgen/totp-stream {:secret secret
                                                   :label "TOTP-Demo"
                                                   :user username
                                                   :image-type :PNG
                                                   :image-size 200})
                          image-bytes (.toByteArray baos)
                          base64-img (.encodeToString (Base64/getEncoder) image-bytes)]
                      (html-response
                       [:html
                        [:head
                         [:meta {:charset "utf-8"}]
                         [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
                         [:title "QR Code for Registration"]
                         [:script {:src "https://cdn.tailwindcss.com"}]]
                        [:body.bg-gray-100.min-h-screen.flex.items-center.justify-center
                         [:div.bg-white.shadow-md.rounded-lg.p-8.w-full.max-w-md.text-center
                          [:h1.text-2xl.font-bold.mb-4 "TOTP Registration"]
                          (if existing?
                            [:div.bg-yellow-100.border.border-yellow-400.text-yellow-700.px-4.py-3.rounded.mb-4
                             "Warning: User already existed, secret key has been updated."]
                            [:div.bg-green-100.border.border-green-400.text-green-700.px-4.py-3.rounded.mb-4
                             "New user registered!"])
                          [:p.mb-4 "Scan the following QR code with your authenticator app (e.g. Google Authenticator) to add the \"TOTP-Demo\" account:"]
                          [:img {:src (str "data:image/png;base64," base64-img)
                                 :class "mx-auto mb-4"}]
                          [:p.mb-4 "After scanning, you can login using the 6-digit code generated by your mobile app."]
                          [:a {:href "/login"
                               :class "inline-block bg-blue-500 hover:bg-blue-600 text-white font-bold py-2 px-4 rounded"}
                           "Proceed to Login"]]]]))))))}]
    ["/login"
     {:get (fn [_]
             (login-page nil))
      :post (fn [req]
              (let [params (:params req)
                    username (-> params :username str/trim)
                    token-str (-> params :token str/trim)
                    users (load-users)
                    user-data (get users username)
                    secret (get user-data :secret)
                    token (try
                            (Long/parseLong token-str)
                            (catch Exception _ nil))]
                (cond
                  (str/blank? username)
                  (login-page "Please provide a username!")
                  (str/blank? token-str)
                  (login-page "Please enter the 6-digit code!")
                  (nil? secret)
                  (login-page "Unknown username or TOTP key not registered.")
                  (nil? token)
                  (login-page "The code format is invalid!")
                  (ot/is-valid-totp-token? token secret)
                  {:status 302
                   :headers {"Location" "/protected"}
                   :session {:user username}}
                  :else
                  (login-page "Invalid code. Please try again."))))}]
    ["/protected"
     {:get (fn [req]
             (if-let [username (get-in req [:session :user])]
               (protected-page username)
               {:status 302 :headers {"Location" "/login"}}))}]]))

(def app
  (wrap-defaults
   (ring/ring-handler app-routes (ring/create-default-handler))
   (-> site-defaults
       (assoc-in [:security :anti-forgery] false))))

(defn -main []
  (println "Server started on port 3001. Browse to http://localhost:3001/")
  (run-jetty #'app {:port 3001, :join? false})
  (println "Press Ctrl+C to stop."))
