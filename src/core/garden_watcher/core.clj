(ns garden-watcher.core
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.java.io :as jio]
   [clojure.java.classpath :as cp]
   [clojure.tools.namespace.file :as t.n.file]
   [clojure.tools.namespace.parse :as t.n.p]
   [garden.core :as garden]
   [hawk.core :as hawk]
   )
  (:import
   java.io.File
   ))


(set! *warn-on-reflection* true)


;;


(s/def ::asset-path string?)
(s/def ::compile-options (s/keys :req-un [::asset-path]))


;;


(def ^:private cp-dirs (set (map (memfn ^File getCanonicalPath) (cp/classpath-directories))))


(defn- path-in-cp?
  [path]
  (contains? cp-dirs (.getCanonicalPath (jio/file path))))


;;


(defn- strip-slash-right
  [s]
  (if (str/ends-with? s "/")
    (subs s 0 (dec (count s)))
    s))


(defn- prepend-root-path
  [s root-path]
  (if (str/starts-with? s "/")
    s
    (str (strip-slash-right root-path) "/" s)))


(defn- clojure-file?
  [file]
  (let [path (str file)]
    (or (str/ends-with? path ".clj")
        (str/ends-with? path ".cljc"))))


(defn- read-file-ns-decl
  "Return namespace"
  [file read-opts]
  (when (clojure-file? file)
    (try
      (t.n.file/read-file-ns-decl file read-opts)
      (catch Throwable _ nil))))


(defn- meta-from-ns-decl
  [decl]
  (transduce
    (comp
      (drop-while symbol?)
      (take-while
        (complement sequential?)))
    (fn
      ([ret] ret)
      ([ret x]
       (if (map? x)
         (merge-with merge ret x)
         ret)))
    (meta (t.n.p/name-from-ns-decl decl))
    decl))


(defn- watched-ns?
  [ns]
  (:garden/watch (meta ns)))


(defn- watched-ns-decl?
  [decl]
  (:garden/watch (meta-from-ns-decl decl)))


(defn reload-and-compile!
  "Reload the given `file`, then find all vars with a :garden metadata in the
  corresponding namespace, and compile those to CSS. The target `file` is either
defined in the :garden metadata as :output-to, or it's derived from the var
name as resources/public/css/<name>.css"
  [file compile-options]
  (when-let [decl (read-file-ns-decl file {})]
    (let [ns-sym (t.n.p/name-from-ns-decl decl)]
      (when (if-let [ns (find-ns ns-sym)]
              (watched-ns? ns)
              (watched-ns-decl? decl))
        (let [ns-sym (t.n.p/name-from-ns-decl decl)]
          (require ns-sym :reload)
          (doseq [[sym var] (ns-publics ns-sym)]
            (when-let [garden-meta (-> var meta :garden)]
              (let [garden-meta (if (map? garden-meta) garden-meta {})
                    asset-path  (:asset-path compile-options)
                    flags       (merge
                                  (dissoc compile-options :asset-path)
                                  (update garden-meta :output-to
                                    (fn [output-to]
                                      (if (string? output-to)
                                        (prepend-root-path output-to asset-path)
                                        (prepend-root-path (str sym ".css") asset-path)))))]
                (println (str "Garden: compiling #'" ns-sym "/" sym))
                (jio/make-parents (:output-to flags))
                (garden/css flags @var)))))))))


(defn compile-paths
  "Given a list of namespaces (seq of symbol), reloads the namespaces, finds all
  syms with a :garden metadata key, and compiles them to CSS."
  [paths compile-options]
  {:pre [(every? path-in-cp? paths) (s/valid? ::compile-options compile-options)]}
  (run!
    (fn [dirpath]
     (run!
       (fn [^File file]
         (when (.isFile file)
           (reload-and-compile! file compile-options)))
       (file-seq (jio/file dirpath))))
    paths))


(defn- garden-reload-handler
  [compiler-options]
  (fn [_ctx event]
    (when (#{#_:create :modify} (:kind event))
      (let [file (:file event)]
        (reload-and-compile! file compiler-options)))))


(defn start-garden-watcher!
  [paths compile-options]
  (let [handler (garden-reload-handler compile-options)]
    (compile-paths paths compile-options)
    (println "Garden: watching" (str/join ", " paths))
    (hawk/watch! [{:paths paths :handler handler}])))


(defn stop-garden-watcher!
  [hawk]
  (hawk/stop! hawk)
  (println "Garden: stopped watching namespaces."))


;; clj exec


(defn compile-paths-x
  [{:keys [paths compile-options]}]
  (compile-paths paths compile-options))


(defn start-garden-watcher-x
  [{:keys [paths compile-options]}]
  (start-garden-watcher! paths compile-options))


;;


(set! *warn-on-reflection* false)
