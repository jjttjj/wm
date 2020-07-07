(ns demo
  (:require [dev.jt.wm :as wm]
            [goog.dom :as d]
            [goog.events :as gev]
            [goog.string :as gstr]
            [goog.style :as gsty]
            [goog.ui.KeyboardShortcutHandler.EventType :as ksh-ev]
            [clojure.pprint :refer [pprint]])
  (:import (goog.ui KeyboardShortcutHandler)))


(def COUNTER (atom 0))


(def STATE (atom (-> (wm/wm)
                   (wm/edit assoc ::counter @COUNTER))))

(declare render)

(add-watch STATE ::state-change
  (fn [_ _ _ new-state]
    (render new-state)))

(defn split-down [state]
  (-> state
    wm/split-down
    wm/move-down
    (wm/edit assoc ::counter (swap! COUNTER inc))
    wm/move-up))

(defn split-right [state]
  (-> state
    wm/split-right
    wm/move-right
    (wm/edit assoc ::counter (swap! COUNTER inc))
    wm/move-left))

(def keybindings
  {"shift+right" #(swap! STATE wm/move-right)
   "shift+left"  #(swap! STATE wm/move-left)
   "shift+down"  #(swap! STATE wm/move-down)
   "shift+up"    #(swap! STATE wm/move-up)
   "ctrl+x 2"    #(swap! STATE split-down)
   "ctrl+x 3"    #(swap! STATE split-right)
   "ctrl+x 1"    #(swap! STATE wm/maximize)
   "ctrl+x 0"    #(swap! STATE wm/delete)})

(def reset-css-str
  "html{box-sizing:border-box;font-size:16px}*,*:before,*:after{box-sizing:inherit}body,h1,h2,h3,h4,h5,h6,p,ol,ul{margin:0;padding:0;font-weight:400}ol,ul{list-style:none}img{max-width:100%;height:auto;font-size:20px;}")

(defn elem-fn [tag]
  (fn [props & kids]
    (apply d/createDom tag
      (clj->js props)
      (clj->js (flatten kids)))))

(def div (elem-fn "DIV"))
(def span (elem-fn "SPAN"))
(def sup (elem-fn "SUP"))
(def sub (elem-fn "SUB"))

(defn icon [html-entity-str]
  (div {:style "font-size:3em;"}
    (gstr/unescapeEntities html-entity-str)))

(defn hover [color]
  {:onmouseover #(set! (.-target.style.background %) color)
   :onmouseout  #(set! (.-target.style.background %) "none")})

(defn ratio-el [r]
  (let [[n d] (wm/native-vec r)]
    (span {}
      (sup {} (str n))
      (gstr/unescapeEntities "&frasl;")
      (sub {} (str d)))))

(defn render-window [{:keys [x y h w ::counter] :as win}]
  (div {:style (str "position:relative;height:100%;padding:0 5px 5px;overflow:hidden;")
        :onclick #(swap! STATE wm/set-loc (wm/->coords win))}
    (div {:style "position:absolute;"}
      (icon "&nwarr;")
      "(" (ratio-el x) "," (ratio-el y) ")")
    (div {:style "position:absolute;top:50%;"}
      (icon "&varr;")
      "Height: " (ratio-el h))
    (div {:style "position:absolute;left:50%;bottom:5px;text-align:center;"}
      (icon "&harr;")
      "Width: " (ratio-el w))
    (div {:style (str "height:100%;" "width:100%;" "display:flex;"
                   "flex-direction:column;" "justify-content:center;"
                   "align-items:center;")}
      (div {:style "margin-bottom:10px;"} "count:" (str counter))
      (div {:style "display:flex;z-index:1000"}
        (div (merge
               {:style
                (str "border:1px solid black;" "cursor:pointer;"
                  "box-shadow:0px 15px 20px lightgrey;" "margin-right:15px;")
                :onclick (fn [_]
                           (swap! STATE wm/set-loc (wm/->coords win))
                           (swap! STATE split-right))}
               (hover "lightblue"))
          (icon "&#8614;"))
        (div (merge
               {:style
                (str "width:2.5em;" "border:1px solid black;" "cursor:pointer;"
                  "box-shadow:0px 15px 20px lightgrey;" "text-align:center")
                :onclick (fn [_]
                           (swap! STATE wm/set-loc (wm/->coords win))
                           (swap! STATE split-down))}
               (hover "lightblue"))
          (icon "&#8615;"))))))

(defn render-windows [{::wm/keys [grid loc] :as state}]
  (div {:style "font-family:monospace"}
    (for [{:keys [x y h w elem] :as win} grid]
      (div {:style
            (str
              "position:absolute;border:"
              (if (= loc (wm/->coords win))
                "2px solid blue"
                "1px solid black")
              ";"
              "top:" (wm/pct y) "%;left:" (wm/pct x) "%;height:" (wm/pct h)
              "%;width:" (wm/pct w) "%;")}
        (render-window win)))))

(defn legend []
  (div {:style (str
                 "position:   absolute;"
                 "bottom:     10px;"
                 "right:      10px;"
                 "width:      270px;"
                 "border:     1px solid black;"
                 "background: lightyellow;"
                 "opacity:    0.60;"
                 "padding:    10px;")}
    (div
      (merge
        {:style
         "position:absolute;right:5px;top:5px;border:1px solid grey;cursor:pointer;font-family:monospace"
         :onclick #(set! (.-target.parentNode.style.display %) "none")}
        (hover "orange"))
      "X")
    (div {:style "font-family:sans-serif"}
      "(ctrl-x, release, then number)"
      (div {}
        "Ctrl-x 3"
        ":  Split Vertically")
      (div {}
        "Ctrl-x 2"
        ": Split Horizontally")
      (div {}
        "Ctrl-x 1"
        ": Maximize current window")
      (div {}
        "Ctrl-x 0"
        ": Delete current window")
      (div {}
        "Shift + Arrow keys:"
        ": move around"))))

(defn init-html []
  (d/append js/document.head (d/createDom "style" #js{} reset-css-str))
  (d/append js/document.body (legend)))

(defn render [state]
  (let [app-el (.getElementById js/document "app")]
    (set! (.-innerHTML app-el) "")
    (d/append app-el (render-windows state))))

;;; Keyboard shortcuts

(defn shortcut-id [x] (hash x))

(defn shortcut-handler []
  (KeyboardShortcutHandler. js/window))

(defn reg [ksh chord-str f]
  (gev/listen ksh (str ksh-ev/SHORTCUT_PREFIX (shortcut-id f)) f)
  (.registerShortcut ksh (str (shortcut-id f)) chord-str))

(defn unreg [ksh chord-str f]
  (gev/unlisten ksh (str ksh-ev/SHORTCUT_PREFIX (shortcut-id f)) f)
  (.unregisterShortcut ksh chord-str))

(defn reg-keymap [sc-handler keymap]
  (doseq [[chord f] keymap]
    (reg sc-handler chord f)))

(defn unreg-keymap [sc-handler keymap]
  (doseq [[chord f] keymap]
    (unreg sc-handler chord f)))

(def sc (shortcut-handler))

(reg-keymap sc keybindings)

(init-html)

(render @STATE)
