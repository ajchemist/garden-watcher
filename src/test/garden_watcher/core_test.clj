(ns garden-watcher.core-test
  (:require
   [clojure.java.io :as jio]
   [clojure.test :as test :refer [deftest is are testing]]
   [clojure.tools.namespace.file]
   [clojure.tools.namespace.dir]
   [garden-watcher.core :as garden-watcher]
   ))


(defn clean!
  [dir]
  (run!
    #(jio/delete-file % true)
    (reverse (file-seq (jio/file dir)))))


(deftest compile-test
  (clean! "target")
  (garden-watcher/compile-paths
    ["src/test"]
    {:asset-path    "target"
     :pretty-print? false})
  (is (false? (.isFile (jio/file "target/no-style.css"))))
  (is (.isFile (jio/file "target/style.css")))
  (is (.isFile (jio/file "target/style2.css")))
  (println (slurp "target/style.css"))
  )


(defn fire-modify-event!
  [file]
  (spit file (slurp file)))


(deftest watch-test
  (clean! "target")
  (def hawk
    (garden-watcher/start-garden-watcher!
      ["src/test"]
      {:asset-path    "target"
       :pretty-print? false}))
  (fire-modify-event! "src/test/garden_watcher/test/no_style.clj")
  (fire-modify-event! "src/test/garden_watcher/test/style.clj")
  )


(comment


  ((ns garden-watcher.core-test (:require [garden-watcher.core :as watcher]))
   (ns garden-watcher.test.style "" #:garden{:watch true} (:require [garden.core :as garden]))
   (ns garden-watcher.test.style2)
   (ns test (:require [clojure.tools.namespace.file :as tnf] [clojure.tools.namespace.dir])))


  ((ns garden-watcher.core-test (:require [garden-watcher.core :as watcher]))
   (ns garden-watcher.test.style "" #:garden{:watch true} (:require [garden.core :as garden]))
   (ns test (:require [clojure.tools.namespace.file :as tnf] [clojure.tools.namespace.dir])))

  )
