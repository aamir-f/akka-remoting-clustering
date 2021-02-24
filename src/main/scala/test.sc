
val l1 = List(1,2,4, 6, 100)
val l2 = List(2, 200)

(l1 ::: l2).sorted

val m1 = Map(1 -> "one", 2 -> "two")
val m2 = Map(2 -> "two")
m1 -- l2