package learning.clustering

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Address, Props, ReceiveTimeout}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{InitialStateAsEvents, MemberEvent, MemberJoined, MemberRemoved, MemberUp, UnreachableMember}
import akka.dispatch.{PriorityGenerator, UnboundedPriorityMailbox}
import akka.util.Timeout
import akka.pattern.pipe
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.Random

/**
  * Distributed computing: Word Count
  * Master actor on master node, worker actor on worker nodes
  * master actor will split the work and send tasks to workers, and aggregate the results
  * Bonus: application can handle varying loads i.e
  * if a new worker node arrives, the master can figure that out and distribute the work to that newly joined node or
  * if a node dies/leaves, master figures it out, and it can distribute the work between workers it has now
  * Goal: Elastic Distributed Application (elastic, one of reactive manifesto points that reactive systems are elastic)
  */
class ClusterWordCountPriorityMailbox(settings: ActorSystem.Settings, config: Config)
  extends UnboundedPriorityMailbox(
    PriorityGenerator {
      case _: MemberEvent => 0
      case _              => 4
    }
  )

object BiggerClusteringExampleDomain {

  case class ProcessFile(fileName: String)

  case class ProcessLine(line: String, aggregator: ActorRef)

  case class ProcessLineResult(count: Int)

}

class Master extends Actor with ActorLogging {

  import context.dispatcher
  import BiggerClusteringExampleDomain._

  /**
    * println(self) ==> Actor[akka://WordCountCluster/user/master#301874455]
    * println(self.path) ==> akka://WordCountCluster/user/master
    * println(self.path.address) ==> akka://WordCountCluster
    * MemberUp(member-address) => akka://WordCountCluster@localhost:2551
    */
  implicit val timeOut: Timeout = Timeout(3.seconds)
  val cluster = Cluster(context.system)

  var workers: Map[Address, ActorRef] = Map() // we assume one worker per node
  var pendingRemove: Map[Address, ActorRef] = Map() //quarantined workers when marked as Unreachable


  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent], classOf[UnreachableMember])
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive: Receive = handleClusterEvents.orElse(handleWorkerRegistration).orElse(handleJob)

  def handleClusterEvents: Receive = {
    case MemberJoined(member)                 =>
      log.info(s"==New member joined==: ${member.address}")
    case MemberUp(member)                     =>
      log.info(s"==Member is up==: ${member.address}")
      if (pendingRemove.contains(member.address)) {
        pendingRemove = pendingRemove - member.address
      } else {
        val workerSelection = context.actorSelection(s"${member.address}/user/worker")
        workerSelection.resolveOne().map(workerRef => (member.address, workerRef)).pipeTo(self)
      }
    case MemberRemoved(member, previousState) =>
      log.info(s"==Member removed==: ${member.address}, after state: $previousState")
      workers = workers - member.address
    case UnreachableMember(member)            =>
      log.info(s"==Member detected as Unreachable==: ${member.address}")
      val workerOptional = workers.get(member.address)
      workerOptional.foreach { workerRef =>
        pendingRemove = pendingRemove + (member.address -> workerRef)
      }
    case m: MemberEvent                       =>
      log.info(s"==Another member event I don't care about== : $m")
  }

  def handleWorkerRegistration: Receive = {
    case pair: (Address, ActorRef) =>
      log.info(s"==Registering worker==: $pair")
      workers = workers + pair
  }

  def handleJob: Receive = {
    case ProcessFile(filename)         =>
      val aggregator = context.actorOf(Props[Aggregator], "aggregator")
      scala.io.Source.fromFile(filename).getLines().foreach { line =>
        self ! ProcessLine(line, aggregator) // again problem, mailbox will contains ~4k messages, so events won't get priority
        //Thread.sleep(10) // will block this actor till filelines * 10 = 30 sec app. so master will not be able to process any new MemberEvents, so it can't up the new workers added, problem, ahhh
      }
    case ProcessLine(line, aggregator) =>
      val workerIndex = Random.nextInt((workers -- pendingRemove.keys).size) //Math.floor
      val worker: ActorRef = (workers -- pendingRemove.keys).values.toSeq(workerIndex)
      worker ! ProcessLine(line, aggregator)
     // Thread.sleep(10)
  }

}

class Worker extends Actor with ActorLogging {

  import BiggerClusteringExampleDomain._

  override def receive: Receive = {
    case ProcessLine(line, aggregator) =>
      log.info(s"Processing: $line")
      aggregator ! ProcessLineResult(line.split(" ").length)
      Thread.sleep(40)
  }
}

class Aggregator extends Actor with ActorLogging {

  import BiggerClusteringExampleDomain._

  context.setReceiveTimeout(3.second)

  override def receive: Receive = online(0)

  def online(totalCount: Int): Receive = {
    case ProcessLineResult(count) =>
      context.become(online(totalCount + count))
    case ReceiveTimeout           =>
      log.info(s"\n\n===TOTAL COUNT===: $totalCount")
      context.setReceiveTimeout(Duration.Inf)
  }
}

object SeedNodes extends App {

  import BiggerClusteringExampleDomain._

  def createNode(port: Int, role: String, props: Props, actorName: String) = {

    val config = ConfigFactory.parseString(
      s"""
         |akka.cluster.roles = [s"$role"]
         |akka.remote.artery.canonical.port = $port
         |""".stripMargin
    ).withFallback(ConfigFactory.load("clustering/count_words_proj.conf"))
    val system = ActorSystem("WordCountCluster", config)
    system.actorOf(props, actorName)
  }

  /**
    * create a physical node with canonical properties, which will look for seed-node once joining for cluster
    */
  val master = createNode(2551, "master", Props[Master], "master")
  createNode(2552, "worker", Props[Worker], "worker")
  createNode(2553, "worker", Props[Worker], "worker")
  Thread.sleep(5000)
  master ! ProcessFile("src/main/resources/txt/lipsum.txt")

}

object AdditionWorker extends App {

  val config = ConfigFactory.parseString(
    """
      |akka.remote.artery.canonical.port = 2554
      |akka.cluster.roles = ["worker"]
      |""".stripMargin
  ).withFallback(ConfigFactory.load("clustering/count_words_proj.conf"))
  val system = ActorSystem("WordCountCluster", config)
  system.actorOf(Props[Worker], "worker")
}

object AdditionWorker2 extends App {

  val config = ConfigFactory.parseString(
    """
      |akka.remote.artery.canonical.port = 2555
      |akka.cluster.roles = ["worker"]
      |""".stripMargin
  ).withFallback(ConfigFactory.load("clustering/count_words_proj.conf"))
  val system = ActorSystem("WordCountCluster", config)
  system.actorOf(Props[Worker], "worker")
}
