(ns vent.util
  (:import [java.lang.reflect Method]))

; Inspired by:
; https://stackoverflow.com/questions/1696693/clojure-how-to-find-out-the-arity-of-function-at-runtime
(defn highest-arity-of
  "Returns the maximum arity of:
    - anonymous functions like `#()` and `(fn [])`.
    - defined functions like `map` or `+`.
    - macros, by passing a var like `#'->`.

  Returns `:variadic` if the function/macro is variadic."
  [f]
  (let [func (if (var? f) @f f)
        methods (->> func class .getDeclaredMethods
                  (map #(vector (.getName %)
                          (count (.getParameterTypes ^Method %)))))
        var-args? (some #(-> % first #{"getRequiredArity"})
                    methods)]
    (if var-args?
      :variadic
      (let [max-arity (->> methods
                        (filter (comp #{"invoke"} first))
                        (sort-by second)
                        last
                        second)]
        (if (and (var? f) (-> f meta :macro))
          (- max-arity 2)
          max-arity)))))

(defn invoke-highest-arity [f & args]
  (let [highest-arity (highest-arity-of f)]
    (cond
      (= highest-arity :variadic) (apply f args)
      (= highest-arity 2) (apply f args)
      (= highest-arity 1) (apply f (take 1 args))
      (= highest-arity 0) (f)
      :else (apply f args))))