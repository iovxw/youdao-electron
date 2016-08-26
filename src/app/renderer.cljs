(ns app.renderer
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [reagent.core :as r]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [clojure.string :as string]))

(def electron    (js/require "electron"))
(def ipcRenderer (.-ipcRenderer electron))
(def remote      (.-remote electron))

(def current-window (.getCurrentWindow remote))

(def youdao-url "http://m.youdao.com/dict?le=eng&q=")

(def result (r/atom [:div#result]))

(enable-console-print!)

(defn on-ipc-msg [channel func]
  (.on ipcRenderer channel func))

(defn get-body-elm [html]
  (second (re-find #"<body[^>]*>([\w\W]{0,}?)</body>" html)))

(defn remove-script-elm [html]
  (string/replace html #"<script[\w\W]{0,}?</script>" ""))

(defn html-to-dom [html]
  (let [div (.createElement js/document "div")]
    (set! (.-innerHTML div) html)
    div))

(defn get-elem-by-tag [parent tag]
  (array-seq (.getElementsByTagName parent tag)))

(defn get-elem-by-class [parent tag]
  (array-seq (.getElementsByClassName parent tag)))

(defn get-phonetic [dom]
  (let [div (first (get-elem-by-tag dom "div"))
        spans (get-elem-by-tag div "span")]
    (remove nil?
            (for [span spans]
              (when-let [phonetic (first (get-elem-by-class span "phonetic"))]
                {:type (-> span .-childNodes array-seq first .-textContent string/trim)
                 :phonetic (.-innerText phonetic)
                 :audio (-> span (get-elem-by-tag "a") first (.getAttribute "data-rel"))})))))

(defn get-value [dom]
  (let [values (-> dom
                   (get-elem-by-class "ec")
                   first
                   (get-elem-by-tag "ul")
                   first
                   (get-elem-by-tag "li"))]
    (for [v values]
      (.-innerText v))))

(defn render-result [word]
  (reset! result [:div#result
                  [:h1#loading "加载中……"]])
  (go
    (try
      (let [url (str youdao-url word)
            resp (<! (http/get url))
            dom (-> resp :body get-body-elm remove-script-elm html-to-dom)]
        (reset! result [:div#result
                        [:h1 word]
                        [:div.phonetic (for [r (get-phonetic dom)]
                                         ^{:key (:type r)}
                                         [:span (:type r)
                                          [:span (:phonetic r)]
                                          [:a {:href (:audio r)}]])]
                        [:ul (for [r (get-value dom)]
                               ^{:key r}
                               [:li r])]]))
      (catch :default e
        (reset! result [:div#result
                        [:h1#error "加载出错"]])))))

(defn body []
  [:div#box [:div#close {:on-mouse-over #(.hide current-window)}]
   @result])

(defn init []
  (on-ipc-msg "translate" #(render-result %2))
  (r/render-component [body] (.-body js/document)))
