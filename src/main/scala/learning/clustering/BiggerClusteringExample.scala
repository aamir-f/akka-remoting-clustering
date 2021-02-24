package learning.clustering

import akka.actor.{Actor, ActorLogging, ActorRef, Address}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{InitialStateAsEvents, MemberEvent, MemberJoined, MemberRemoved, MemberUp, UnreachableMember}
import akka.util.Timeout
import akka.pattern.pipe

import scala.concurrent.duration.DurationInt

/**
  * Distributed computing: Word Count
  * Master actor on master node, worker actor on worker nodes
  * master actor will split the work and send tasks to workers, and aggregate the results
  * Bonus: application can handle varying loads i.e
  * if a new worker node arrives, the master can figure that out and distribute the work to that newly joined node or
  * if a node dies/leaves, master figures it out, and it can distribute the work between workers it has now
  * Goal: Elastic Distributed Application (elastic, one of reactive manifesto points that reactive systems are elastic)
  */
object BiggerClusteringExample extends App {

}

class Master extends Actor with ActorLogging {

  import context.dispatcher

  implicit val timeOut: Timeout = Timeout(3.seconds)
  val cluster = Cluster(context.system)

  var workers: Map[Address, ActorRef] = Map() // we assume one worker per node
  var pendingRemove: Map[Address, ActorRef] = Map() //quarantined workers when marked as Unreachable


  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent], classOf[UnreachableMember])
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive: Receive = handleClusterEvents

  def handleClusterEvents: Receive = {
    case MemberJoined(member)                 =>
      log.info(s"New member joined: ${member.address}")
    case MemberUp(member)                     =>
      log.info(s"Member is up: ${member.address}")
      if (pendingRemove.contains(member.address)) {
        pendingRemove = pendingRemove - member.address
      } else {
        val workerSelection = context.actorSelection(s"${member.address}/user/worker")
        workerSelection.resolveOne().map(workerRef => (member.address, workerRef)).pipeTo(self)
      }
    case MemberRemoved(member, previousState) =>
      log.info(s"Member removed: ${member.address}, after state: $previousState")
      workers = workers - member.address
    case UnreachableMember(member)            =>
      log.info(s"Member detected as Unreachable: ${member.address}")
      val workerOptional = workers.get(member.address)
      workerOptional.foreach { workerRef =>
        pendingRemove = pendingRemove + (member.address -> workerRef)
      }
    case m: MemberEvent                       =>
      log.info(s"Another member event I don't care about : $m")
  }

  def handleWorkerRegistration: Receive = {
    case pair: (Address, ActorRef) =>
      log.info(s"Registering worker: $pair")
      workers = workers + pair
  }

}

class Worker extends Actor with ActorLogging {
  override def receive: Receive = {
    case _ => //TODO
  }
}