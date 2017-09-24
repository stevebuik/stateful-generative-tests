(ns stateful-testing.core)

(defn set-contains [set elem]
  (.contains set elem))

(defn set-add [set elem]
  (.add set elem))

(defn set-remove [set elem]
  (.remove set elem))

(defn new-set [class]
  (.newInstance class))
