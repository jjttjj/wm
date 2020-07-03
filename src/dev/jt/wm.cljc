(ns dev.jt.wm
  (:require [com.gfredericks.exact :as e]
            [clojure.core :as core]
            precise.tagged-literals)
  (:refer-clojure :exclude [contains? + - * / mod = > < >= <= min max #?(:clj ratio?)]))

;;; Ratios


(defn r "Creates a ratio from a native integer or a numerator and denominator"
  ([x] (e/string->integer (str x)))
  #?(:clj
     ([n d] (clojure.lang.Ratio. (BigInteger/valueOf n) (BigInteger/valueOf d)))
     :cljs
     ([n d]
      (com.gfredericks.exact.impl/-ratio
        (e/string->integer (str n)) (e/string->integer (str d))))))

(defn ratio? [x]
  (or (e/ratio? x) (e/integer? x)))

(defn native-vec [r]
  (if (e/ratio? r)
    [(e/integer->native (e/numerator r)) (e/integer->native (e/denominator r))]
    [(e/integer->native r) 1]))

(defn pct "Returns a native representation of the a percent of the ratio"
  [r]
  (core/* 100
    (if (e/ratio? r)
      (core//
        (e/integer->native (e/numerator r))
        (e/integer->native (e/denominator r)))
      r)))

;;; Rectangles

(def empty-rect {:x (r 0) :y (r 0) :w (r 1) :h (r 1)})

(defn ->coords
  "Given a rectangle, returns a vector of `[x, y]`"
  [rect]
  ((juxt :x :y) rect))

(defn compare-xy
  "Given two rectangles, compares them by `:x`: then `:y`"
  [r1 r2]
  (let [c (compare (->coords r1) (->coords r2))]
    (if (zero? c)
      ;;if tie, return arbitrary but consistent result
      ;;see
      ;;https://clojuredocs.org/clojure.core/sorted-set-by#example-542692d5c026201cdc327096
      (compare (hash r1) (hash r2))
      c)))

(defn chop|
  "Chop a rectangle vertically at `rel-width`. Returns a vector of two
  rectangles."
  [{:keys [w x] :as rect} rel-width]
  (let [w2 (e/* w rel-width)
        w1 (e/- w w2)
        r1 (assoc rect :w w1)
        r2 (assoc rect :w w2 :x (e/+ x w1))]
    [r1 r2]))

(defn chop-
  "Chop a rectangle horizontal at `rel-height`. Returns a vector of two
  rectangles."
  [{:keys [h y] :as rect} rel-height]
  (let [h2 (e/* h rel-height)
        h1 (e/- h h2)
        r1 (assoc rect :h h1)
        r2 (assoc rect :h h2 :y (e/+ y h1))]
    [r1 r2]))

(defn contains?
  "True if `coord` is contained within `rect`."
  [{x1 :x y1 :y h1 :h w1 :w :as rect} [x y :as coord]]
  (and
    (and (e/<= x1 x) (e/< x (e/+ x1 w1)))
    (and (e/<= y1 y) (e/< y (e/+ y1 h1)))))

;;; Grid

(defn grid
  "Returns a new grid: a set of rectangles sorted by `compare-xy`."
  []
  (sorted-set-by compare-xy empty-rect))

(defn get-at
  "Get the rectangle in the `grid` at the given `coord`"
  [grid [x y :as coord]]
  (some->> grid (filter #(contains? % [x y])) first))

(defn update-at [grid coords f & args]
  "Apply `f` to the rectangle in the `grid` at the given `coord`"
  (let [old-rect (get-at grid coords)
        new-rect (apply f old-rect args)]
    (-> grid
      (disj old-rect)
      (conj new-rect))))

(defn replace-at
  "Apply `f` to the rectangle in the `grid` at the given `coord`. That rectangle
  is removed and the collection resulting from `(f old-rect)` is merged into the
  grid."
  [grid coords f & args]
  (let [old-rect  (get-at grid coords)
        new-rect (apply f old-rect args)]
    (-> grid
      (disj old-rect)
      (into new-rect))))

;;TODO: define at-* in terms of these? would also need "contained-by"
(defn contained-right [grid {x1 :x y1 :y h1 :h w1 :w :as rect}]
  (let [x2 (e/+ x1 w1)
        y2 (e/+ y1 h1)]
    (filter 
      (fn [{:keys [x y h w]}]
        (and
          (e/= x2 x)
          (e/<= y1 y)
          (e/>= y2 (e/+ y h))))
      grid)))

(defn contained-left [grid {x1 :x y1 :y h1 :h w1 :w :as rect}]
  (let [y2 (e/+ y1 h1)]
    (filter 
      (fn [{:keys [x y h w]}]
        (and
          (e/= x1 (e/+ x w))
          (e/<= y1 y)
          (e/>= y2 (e/+ y h))))
      grid)))

(defn contained-down [grid {x1 :x y1 :y h1 :h w1 :w :as rect}]
  (let [y2 (e/+ y1 h1)
        x2 (e/+ x1 w1)]
    (filter 
      (fn [{:keys [x y h w]}]
        (and
          (e/= y2 y)
          (e/<= x1 x)
          (e/>= x2 (e/+ x w))))
      grid)))

(defn contained-up [grid {x1 :x y1 :y h1 :h w1 :w :as rect}]
  (let [x2 (e/+ x1 w1)]
    (filter 
      (fn [{:keys [x y h w]}]
        (and
          (e/= y1 (e/+ y h))
          (e/<= x1 x)
          (e/>= x2 (e/+ x w))))
      grid)))

;;Note: these return the rect closest to the origin in the given direction. Other strategies are possible.
;;TODO: use relational operators instead of adding small number
(defn at-right [grid {:keys [x y w h] :as loc-rect}]
  (let [x1 (e/+ x w (r 1 1000))]
    (get-at grid [x1 y])))

(defn at-left [grid {:keys [x y w h] :as loc-rect}]
  (let [x1 (e/- x (r 1 1000))]
    (get-at grid [x1 y])))

(defn at-up [grid {:keys [x y w h] :as loc-rect}]
  (let [y1 (e/- y (r 1 1000))]
    (get-at grid [x y1])))

(defn at-down [grid {:keys [x y w h] :as loc-rect}]
  (let [y1 (e/+ y h (r 1 1000))]
    (get-at grid [x y1])))

;;; Navigable grid

(defn wm []
  {::grid (grid) ::loc [(r 0) (r 0)]})

(defn current [state]
  (get-at (::grid state) (::loc state)))

(defn loc [state]
  (->coords (current state)))

(defn set-loc [state loc]
  (assoc state ::loc loc))

(defn edit [state f & args]
  (update state ::grid (fn [g] (apply update-at g (::loc state) f args))))

(defn split-down [{::keys [loc grid] :as state} & {:keys [new-size]
                                                   :or   {new-size (r 1 2)}}]
  (update state ::grid replace-at loc chop- new-size))

(defn split-right [{::keys [loc grid] :as state} & {:keys [new-size]
                                                    :or   {new-size (r 1 2)}}]
  (update state ::grid replace-at loc chop| new-size))

(defn move [state to-fn]
  (let [loc     (current state)
        new-loc (or (to-fn (::grid state) loc) loc)]
    (set-loc state (->coords new-loc))))

(defn move-up [state]
  (move state at-up))

(defn move-down [state]
  (move state at-down))

(defn move-left [state]
  (move state at-left))

(defn move-right [state]
  (move state at-right))

(defn maximize [state]
  (-> state
    (update ::grid empty)
    (update ::grid conj (merge (current state) empty-rect))
    (assoc ::loc [(r 0) (r 0)])))

;;;;wip; still fails for some cases
(defn delete [{::keys [loc] :as state}]
  (let [priority        [:up :right :down :left]
        rect            (current state)
        grid            (::grid state)
        dir-map
        (zipmap priority
          (concat
            (map #(when (e/= (:w rect) (reduce e/+ (map :w %))) %)
              ((juxt contained-up contained-down) grid rect))
            (map #(when (e/= (:h rect) (reduce e/+ (map :h %))) %)
              ((juxt contained-left contained-right) grid rect))))
        [dir to-expand] (first (keep (fn [dir]
                                       (when-let [expand-to (dir-map dir)]
                                         [dir expand-to])) priority))
        results         (cond
                          (#{:up :down} dir)
                          (map #(-> %
                                  ;;this merge, and below, fails to work in some cases.
                                  (merge (zipmap [:x :y] (first (sort [loc (->coords %)]))))
                                  (update :h e/+ (:h rect))) to-expand)

                          (#{:left :right} dir)
                          (map #(-> %
                                  (merge (zipmap [:x :y] (first (sort [loc (->coords %)]))))
                                  ;;(update :x min (:x rect))
                                  ;;(update :y min (:y rect))
                                  (update :w e/+ (:w rect))) to-expand))

        grid (reduce disj grid (conj to-expand rect))
        grid (into grid results)]
    (cond-> state
      (not-empty results) (-> (assoc ::grid grid)
                            (set-loc (->coords (first results)))))))
