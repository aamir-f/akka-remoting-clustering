package learning.clustering

import akka.actor.{Actor, ActorLogging, ActorSystem, Address, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import com.typesafe.config.ConfigFactory
import learning.clustering.ClusterBasics.ClusterSubscriber
object ClusterBasics extends App {

  class ClusterSubscriber extends Actor with ActorLogging {

    val cluster = Cluster(context.system)

    /**
      * So the ActorSystem which holds this actor, or if its started from ActorSystem in cluster,
      * will send events[MemberEvent, UnreachableMember in this case, as we subscribed to them]
      * to this actor whenever a new member event was triggered
      * initialStateMode = InitialStateAsEvents: If the Node/ActorSystem holding this Actor joins the Cluster later,
      * then this Actor will receive messages as if it has started at the beginning of the cluster i.e
      * Every single history element of members joining and leaving the cluster. Also,
      * initialStateMode = InitialStateAsEvents, When using this subscription mode the events corresponding
      * to the current state will be sent to the subscriber to mimic what you would have seen
      * if you were listening to the events when they occurred in the past.
      * initialStateMode = InitialStateAsSnapshot: If we just want the state of the cluster at the moment of join.Also,
      * initialStateMode = InitialStateAsSnapshot, When using this subscription mode a snapshot of
      * ClusterEvent.CurrentClusterState will be sent to the subscriber as the first message.
      */
    override def preStart(): Unit = {
      cluster.subscribe(
        self,
        initialStateMode = InitialStateAsEvents, //  ClusterEvent.InitialStateAsSnapshot
        classOf[MemberEvent],
        classOf[UnreachableMember]
      )
    }

    override def postStop(): Unit = cluster.unsubscribe(self)

    override def receive: Receive = {
      case MemberJoined(member)                  => // for seed node joining, we won't receive this message , as that is the node which forms the cluster.
        log.info(s"New Member in town: Member.address => ${member.address}")
      case MemberUp(member)                      =>
        log.info(s"Let's say welcome to the newest member, having address => ${member.address}")
      case MemberRemoved(member, previousStatus) =>
        log.info(s"Poor member => ${member.address}, was removed from previousStatus: $previousStatus")
      case UnreachableMember(member)             => //A member is considered unreachable by the failure detector
        log.info(s"Uh Oh, member: =. ${member.address}, is unreachable")

      case m: MemberEvent => //any other member event if comes
        log.info(s"Another member event: $m")

    }
  }

  def startCluster(ports: List[Int]) = ports foreach { port =>
    val config = ConfigFactory.parseString(
      s"""
         |akka.remote.artery.canonical.port = $port
         |""".stripMargin
    ).withFallback(ConfigFactory.load("clustering/clustering_basics.conf"))
    val system = ActorSystem("RTJVMCluster", config)
    system.actorOf(Props[ClusterSubscriber], "clusterSubscriber")
  }

  startCluster(List(2551, 2552, 0))
}

object Manual_Registration_Cluster extends App {

  val system = ActorSystem("RTJVMCluster",
    ConfigFactory.load("clustering/clustering_basics.conf").getConfig("manualRegistration")
  )

  val cluster: Cluster = Cluster(system)


  cluster.joinSeedNodes(List(
    Address("akka", "RTJVMCluster", "localhost", 2551), // == AdressFromUriString("akka://RTJVMCluster/locahost:2551")
    Address("akka", "RTJVMCluster", "localhost", 2552)
  ))

  system.actorOf(Props[ClusterSubscriber], "clusterSubscriber")



}