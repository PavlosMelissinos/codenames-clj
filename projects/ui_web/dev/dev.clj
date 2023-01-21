(ns dev
  (:require [clojure.string :as str]
            [hickory.core :refer [parse parse-fragment as-hiccup]]))

(defn remove-hiccup-comments
  "remove elem in coll"
  [coll]
  (remove #(str/starts-with? % "<!--") coll))

(defn html->hiccup
  ([val]
   (html->hiccup val true))
  ([val snippet?]
   (-> (if snippet?
         (map as-hiccup (parse-fragment val))
         (as-hiccup (parse val)))
       first
       vec)))

(defn html-hero->hiccup [val]
  (let [svg (-> val html->hiccup remove-hiccup-comments)]
    {:viewbox (-> svg (nth 1) :viewbox)
     :path (-> (filter #(and (vector? %) (= :path (first %))) svg) first second :d)}))

(defn html-fa->hiccup [val]
  (let [svg (-> val html->hiccup remove-hiccup-comments)]
    {:viewbox (-> svg (nth 1) :viewbox)
     :path (-> (filter #(and (vector? %) (= :path (first %))) svg) first second :d)}))

(comment

  (html-hero->hiccup "<svg xmlns=\"http://www.w3.org/2000/svg\" fill=\"none\" viewBox=\"0 0 24 24\" stroke-width=\"1.5\" stroke=\"currentColor\" class=\"w-6 h-6\">
  <path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M2.25 12l8.954-8.955c.44-.439 1.152-.439 1.591 0L21.75 12M4.5 9.75v10.125c0 .621.504 1.125 1.125 1.125H9.75v-4.875c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21h4.125c.621 0 1.125-.504 1.125-1.125V9.75M8.25 21h8.25\" />
</svg>")
  ,)
