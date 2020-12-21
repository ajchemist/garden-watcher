((nil
  ;; (cider-figwheel-main-default-options . "dev")
  )
 (clojure-mode
  ;; (cider-clojure-cli-global-options . "")
  (cider-clojure-cli-parameters . "-M:provided:test -m nrepl.cmdline --middleware '%s'")
  ;; (cider-default-cljs-repl . figwheel-main)
  (clojure-local-source-test-path . "src/test")))
