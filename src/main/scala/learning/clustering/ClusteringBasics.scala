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
        initialStateMode = InitialStateAsEvents, //if ClusterEvent.InitialStateAsSnapshot, will get ClusterEvent.CurrentClusterState
        classOf[MemberEvent],
        classOf[UnreachableMember]
      )
    }

    override def postStop(): Unit = cluster.unsubscribe(self)

    override def receive: Receive = {
      case MemberJoined(member)                  => // for seed node joining, we won't receive this message , as that is the node which forms the cluster.
        log.info(s"New Member in town: Member.address => ${member.address}")
      case MemberUp(member) if member.hasRole("numberCruncher") =>
        log.info(s"HELLO BROTHER: ${member.address}")
      case MemberUp(member) =>
        log.info("\n======" + member.roles + "\n")
        log.info(s"Let's say welcome to the newest member: ${member.address}")
      case MemberRemoved(member, previousStatus) => //e.g previous state = downed, then MemberRemoved
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

  val system = ActorSystem("RTJVMCluster", // then name must be same as the cluster ur joining
    ConfigFactory.load("clustering/clustering_basics.conf").getConfig("manualRegistration")
  )

  val cluster: Cluster = Cluster(system)


  def joinExistingCluster =
  cluster.joinSeedNodes(List(
    Address("akka", "RTJVMCluster", "localhost", 2551), // == AdressFromUriString("akka://RTJVMCluster/locahost:2551")
    Address("akka", "RTJVMCluster", "localhost", 2552)
  ))

  //Try to join this cluster node with address specified, 58747 will be this node's initial point of context
  def joinExistingNode =  cluster.join(Address("akka", "RTJVMCluster", "localhost", 52910))

  // will create a new cluster for itself, as no seed nodes specified for it
  def joinMyself = cluster.join(Address("akka", "RTJVMCluster", "localhost", 2555))

  joinMyself
  system.actorOf(Props[ClusterSubscriber], "clusterSubscriber")





}

/**
  * Order of seed-nodes matter, while running if 2552 joins cluster first, but in config we have defined seed-nodes as
  * [2551, 2552], so 2552 while joining will look for seed-node 2551 but its not up yet, so it won't be able to join,
  * and after retrying, will join it later.
  * ErrorMessage:Couldn't join seed nodes after [3] attempts, will try again
  */

object ClusterBasics_2 extends App {

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
        initialStateMode = InitialStateAsEvents, //if ClusterEvent.InitialStateAsSnapshot, will get ClusterEvent.CurrentClusterState
        classOf[MemberEvent],
        classOf[UnreachableMember]
      )
    }

    override def postStop(): Unit = cluster.unsubscribe(self)

    override def receive: Receive = {
      case MemberJoined(member)                  => // for seed node joining, we won't receive this message , as that is the node which forms the cluster.
        log.info(s"New Member in town: Member.address => ${member.address}")
      case MemberUp(member) if member.hasRole("numberCruncher") =>
        log.info(s"HELLO BROTHER: ${member.address}")
      case MemberUp(member) =>
        log.info("\n======" + member.roles + "\n")
        log.info(s"Let's say welcome to the newest member: ${member.address}")
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
    Thread.sleep(5000)
  }

  startCluster(List(2552, 2551, 0))
}
