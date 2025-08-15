;; Math operations test
(def pi 3.14159)
(def radius 5)

(defn circle-area [r]
  (* pi r r))

(defn circle-circumference [r]  
  (* 2 pi r))

(println "Testing circle calculations:")
(println "Radius:" radius)
(println "Area:" (circle-area radius))
(println "Circumference:" (circle-circumference radius))

{:radius radius
 :area (circle-area radius)
 :circumference (circle-circumference radius)}