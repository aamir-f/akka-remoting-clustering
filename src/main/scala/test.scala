import akka.actor.{Actor, ActorSystem, Props, ReceiveTimeout}

import scala.util.Random

object Test extends App {

  val l = List(22,20,13,42,57,65,17,87,19)
  val r = new Random()
  val va = r.nextInt(l.size)
  println{
    va
  }
  val item = l(va)
  println(item)
}

object Test2 extends App {

  val l2 = List()


  val m1 = Map(1 -> "one", 2 -> "two")

  val s = Random.nextInt((m1 -- l2).size)
  println(s)
  val x = (m1 -- l2).values.toList
  val y =  x.toSeq(s)

  println(y)
}



object TimeOutTest extends App {
  class ActorTimeOut extends Actor {
    import scala.concurrent.duration._
    context.setReceiveTimeout(3 seconds)

    override def receive: Receive = {
      case x: String => println("string received")
      case ReceiveTimeout =>
        println(" shit i am waiting main")
        context.setReceiveTimeout(Duration.Inf) // stops timeout
    }

  }
  val system = ActorSystem("TimeoutASS")
  val actorRef = system.actorOf(Props[ActorTimeOut], "timeouttestactor")

  for (i <- 1 to 5) {
    if(i == 5) Thread.sleep(5000)
    else Thread.sleep(2000)
    actorRef ! s"hello: $i"
  }
}