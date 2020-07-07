# wm

`wm` is a clojure(script) library which provides an immutable datastructure that's useful for implementing a tiling window manager. 

![](wm.png)

## Demo

[Live Demo](https://jt.dev/demos/wm)

Demo code is [here](dev/demo.cljs). 
To run the demo locally, clone this repo, `cd` into the `demo` directory and run:
```
clj -A:demo
```

## Usage

Add the following to your `:deps` map in your deps.edn:

```clojure
dev.jt/wm {:git/url "https://github.com/jjttjj/wm.git"
           :sha "108d7aedbd5fa7e930eb39ddecf2a20d8074425e"}
```

The `wm` function creates a window manager which manages a grid of rectangles and a current location. A rectangle is just a map with `:x`, `:y`, `:h`: and `:w` keys. All of these are either ratios or integer types from the [`com.gfredericks/exact`](https://github.com/gfredericks/exact) library. The grid is a sorted set of rectangles sorted by `:x`, `:y`. A coordinate is a vector of `:x` and `:y`. Location is the current coordinate.

```clojure
(ns my.ns
 (:require [dev.jt.wm :as wm]))

(def state (wm/wm))
```

- `(r numerator denominator)` takes two numbers and returns a ratio.
- `(split-down state)`/`split-right`: splits the current rectangle. Takes an optional keyword arg `:new-size` which is a ratio representing the size of the new rectangle relative to the old one. Defaults to 1/2 (ie `(r 1 2)`.
- `(move-up state)`/`move-down`/`move-left`/`move-right`: navigate in the given direction.
- `(edit state f & args)`: applies a function to the current rectangle. Can provide extra args to that function.
- `(maximize state)`: expands the current window to the full size of the screen. 
- `(delete state)`: deletes the current rectangle and attempts to fill in it's area by expanding some bordering recangles.


```clojure

(-> (wm/wm)
    wm/split-down
    wm/move-down
    (wm/edit assoc ::my-content "new split!")
    wm/move-up)

```

## License

Copyright © 2020 Justin Tirrell

Released under the MIT license.
