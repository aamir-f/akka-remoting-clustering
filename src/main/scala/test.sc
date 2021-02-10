var count = 0
def complexCompute(): Int = { count +=1; println("eval " + count); count }

val iter: Iterator[Int] = Iterator.continually[Int] { complexCompute() }
iter.takeWhile(_ < 3).foreach(println)

val workers = List(1,2,3,4)
val sentences = Array("hi", "how", "are", "you")
workers.zip(sentences).foreach { pair =>
  val (worker, sentence) = pair
   println(worker)
  println(sentence)
  println()
}