package part2_remoting

import akka.actor.{Actor, ActorIdentity, ActorLogging, ActorRef, ActorSystem, Identify, PoisonPill, Props}
import akka.routing.FromConfig
import com.typesafe.config.{Config, ConfigFactory}

object WordCountDomain1 {
  case class Initialize1(nWorkers: Int)
  case class WordCountTask1(text: String)
  case class WordCountResult1(count: Int)
}

class WordCountWorker1 extends Actor with ActorLogging {
  import WordCountDomain1._

  override def receive: Receive = {
    case WordCountTask1(text) =>
      log.info(s"I'm processing: $text")
      sender() ! WordCountResult1(text.split(" ").length)
  }
}

class WordCountMaster1 extends Actor with ActorLogging {
  import WordCountDomain1._

  override def receive: Receive = {
    case Initialize1(nWorkers) =>
      log.info("Master initializing...")
      val workerSelections = (1 to nWorkers).map(id => context.actorSelection(s"akka://WorkersSystem@localhost:2552/user/wordCountWorker$id"))
      workerSelections.foreach(_ ! Identify("rtjvm"))
      context.become(initializing(List(), nWorkers))
  }

  def initializing(workers: List[ActorRef], remainingWorkers: Int): Receive = {
    case ActorIdentity("rtjvm", Some(workerRef)) =>
      log.info(s"Worker identified: $workerRef")
      if (remainingWorkers == 1) context.become(online(workerRef :: workers, 0, 0))
      else context.become(initializing(workerRef :: workers, remainingWorkers - 1))
  }

  def online(workers: List[ActorRef], remainingTasks: Int, totalCount: Int): Receive = {
    case text: String =>
      // split it into sentences
      val sentences = text.split("\\. ")
      // send sentences to workers in turn
      Iterator.continually(workers).flatten.zip(sentences.iterator).foreach { pair =>
        val (worker, sentence) = pair
        worker ! WordCountTask1(sentence)
      }
     context.become(online(workers, remainingTasks + sentences.length, totalCount))

    case WordCountResult1(count) =>
      if (remainingTasks == 1) {
        log.info(s"TOTAL RESULT: ${totalCount + count}")
        workers.foreach(_ ! PoisonPill)
        context.stop(self)
      } else {
        println("Remaining Tasks: " + remainingTasks)
        println(s"count total: ${totalCount + count}")
        context.become(online(workers, remainingTasks - 1, totalCount + count))
      }
  }
}

object MasterApp1 extends App {
  import WordCountDomain1._

  val config: Config = ConfigFactory.parseString(
    """
      |akka.remote.artery.canonical.port = 2551
    """.stripMargin)
    .withFallback(ConfigFactory.load("part2_remoting/remoteActorsExercise1.conf"))

  val system = ActorSystem("MasterSystem", config)

  val master = system.actorOf(Props[WordCountMaster1], "wordCountMaster")
  master ! Initialize1(3)
  Thread.sleep(1000)

  scala.io.Source.fromFile("src/main/resources/txt/lipsum.txt").getLines().foreach { line =>
    master ! line
  }
}

object WorkersApp1 extends App {
  val config = ConfigFactory.parseString(
    """
      |akka.remote.artery.canonical.port = 2552
    """.stripMargin)
    .withFallback(ConfigFactory.load("part2_remoting/remoteActorsExercise1.conf"))

  val system = ActorSystem("WorkersSystem", config)
  (1 to 3).map(i => system.actorOf(Props[WordCountWorker1], s"wordCountWorker$i"))
}