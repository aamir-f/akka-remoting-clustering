package learning

import akka.actor.{Actor, ActorIdentity, ActorLogging, ActorRef, ActorSelection, ActorSystem, Identify, PoisonPill, Props}
import akka.routing.FromConfig
import com.typesafe.config.ConfigFactory

import scala.collection.immutable

object WordCountDomain {

  final case class Initialize(workers: Int)

  final case class WordCountTask(text: String)

  final case class WordCountResponse(count: Int)

}

class WordCountMaster extends Actor with ActorLogging {

  val workerRouter: ActorRef = context.actorOf(FromConfig.props(Props[WordCountWorker]), "workerRouter")

  import WordCountDomain._

  override def receive: Receive = {
    //case Initialize(nWorkers) =>
      //log.info("Master initializing...")
      onlineWithRouter(0, 0)
  }

  /**
   * It's easy to use Routers with remotely deployed routees to create a distributed application for
    * computationally expensive task.
    * Just nearly 20 lines of code using routers
   */
  def onlineWithRouter(remainingTasks: Int, totalCount: Int): Receive = {
    case text: String =>
      val sentences: Array[String] = text.split("\\. ")
      sentences.foreach(sentence => workerRouter ! WordCountTask(sentence))
      context.become(onlineWithRouter(sentences.length + remainingTasks, totalCount))
    case WordCountResponse(count) =>
      if(remainingTasks == 1) {
        log.info(s"Total Words : ${count + totalCount}")
        context.stop(self)
      } else context.become(onlineWithRouter(remainingTasks - 1, totalCount + count))
  }

  /**not used anymore, using remoting*/
  def waitingToInitializeworkers(nWorkers: Int) = {
      val workers = (1 to nWorkers).map(id => context.actorOf(Props[WordCountWorker], s"wordCountWorker$id"))
      context.become(online(workers.toList, 0, 0))
  }

  /**not used anymore, using Actor Identification Protocol*/
  def identifyWorkers(nWorkers: Int) = {
    val workers: immutable.Seq[ActorSelection] = (1 to nWorkers).map(x => context.actorSelection(s"akka://Remote_Worker_AS@localhost:2552/user/WordCountWorker$x"))
    workers foreach (_ ! Identify("word_count_project"))
    context.become(initializing(Nil, nWorkers))
  }

  /**not used anymore*/
  def initializing(workers: List[ActorRef], nWorkers: Int): Receive = {
    case ActorIdentity("word_count_project", Some(workerRef)) =>
      log.info(s"Worker identified: $workerRef")
      if (nWorkers == 1) context.become(online(workerRef :: workers, 0, 0))
      else context.become(initializing(workerRef :: workers, nWorkers - 1))
  }

  /**not used anymore*/
  def online(workers: List[ActorRef], remainingTasks: Int, totalCount: Int): Receive = {
    case text: String =>
      val sentences: Array[String] = text.split("\\. ")
      Iterator.continually(workers).flatten.zip(sentences.iterator).foreach { pair =>
        val (workerRef, sentence) = pair
        workerRef ! WordCountTask(sentence)
      }
      context.become(online(workers, sentences.length + remainingTasks, totalCount))
    case WordCountResponse(count) =>
      if(remainingTasks == 1) {
        log.info(s"Total Words : ${count + totalCount}")
        workers.foreach(_ ! PoisonPill)
        context.stop(self)
      } else context.become(online(workers, remainingTasks - 1, totalCount + count))
  }
}

object MasterApp_JVM extends App {

  import WordCountDomain._

  val config = ConfigFactory.parseString(
    """
      |akka.remote.artery.canonical.port= 2551
      |""".stripMargin).
  withFallback(ConfigFactory.load("remoting/exercise_deploying_remotely.conf"))

  val masterRemoteAS = ActorSystem("Remote_Master_AS", config)
  val master = masterRemoteAS.actorOf(Props[WordCountMaster], "WordCountMaster")
  //master ! Initialize(3)
  Thread.sleep(2000)

  scala.io.Source.fromFile("src/main/resources/txt/lipsum.txt").getLines().foreach { line =>
    master ! line
  }
}

object WorkerApp_JVM extends App {

  val config = ConfigFactory.parseString(
    """
      |akka.remote.artery.canonical.port= 2552
      |""".stripMargin
  ).withFallback(ConfigFactory.load("remoting/exercise_deploying_remotely.conf"))

  val workerActorSystem = ActorSystem("Remote_Worker_AS", config)
}

class WordCountWorker extends Actor with ActorLogging {

  import WordCountDomain._

  override def receive: Receive = {
    case WordCountTask(text) =>
      log.info(s"I'm processing: $text")
      sender() ! WordCountResponse(text.split(" ").length)
  }
}