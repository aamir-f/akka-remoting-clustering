import scala.util.Random

val l2 = List()


val m1 = Map(1 -> "one", 2 -> "two")

val s = (m1 -- l2).size
val x = (m1 -- l2).values.toList
 val y =  x.toSeq(s - 1)
