(ns garden-watcher.test.style
  ""
  {:garden/watch true}
  (:require
   [garden.core :as garden]
   ))


(def ^{:garden {}}
  style
  [[:.test {:margin [[0 :auto]]}]])
