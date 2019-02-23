(ns vent.util-test
  (:require
    [clojure.test :refer :all]

    [vent.util :refer [highest-arity-of]]))

; Inspired by:
; https://stackoverflow.com/questions/1696693/clojure-how-to-find-out-the-arity-of-function-at-runtime

(defmacro m ([a]) ([a b]))
(defmacro mx [])

(deftest test-arity
  (testing "with an anonymous #(… %1) function"
    (is (= 1 (highest-arity-of #(+ % 32))))
    (is (= 1 (highest-arity-of #(+ %1 32))))
    (is (= 2 (highest-arity-of #(+ %1 %2))))
    (is (= 13 (highest-arity-of
                #(+ %1 %2 %3 %4 %5 %6 %7 %8 %9 %10 %11 %12 %13))))
    (is (= :variadic (highest-arity-of #(apply + %&))))
    (is (= :variadic (highest-arity-of #(apply + % %&)))))

  (testing "with an anonymous (fn [] …) function"

    (testing "single body"
      (is (= 0 (highest-arity-of (fn []))))
      (is (= 1 (highest-arity-of (fn [a]))))
      (is (= 2 (highest-arity-of (fn [a b]))))
      (is (= 20 (highest-arity-of
                  (fn [a b c d e f g h i j k l m n o p q r s t]))))
      (is (= :variadic (highest-arity-of (fn [a b & more])))))

    (testing "multiple bodies"
      (is (= 0 (highest-arity-of (fn ([])))))
      (is (= 1 (highest-arity-of (fn ([a])))))
      (is (= 2 (highest-arity-of (fn ([a]) ([a b])))))
      (is (= :variadic (highest-arity-of (fn ([a]) ([a b & c])))))))

  (testing "with a defined function"
    (is (= :variadic (highest-arity-of map)))
    (is (= :variadic (highest-arity-of +)))
    (is (= 1 (highest-arity-of inc))))

  (testing "with a var to a macro"
    (is (= :variadic (highest-arity-of #'->)))
    (is (= 2 (highest-arity-of #'m)))
    (is (= 0 (highest-arity-of #'mx)))))
