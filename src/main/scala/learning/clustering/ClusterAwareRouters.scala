package learning.clustering

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.routing.FromConfig
import com.typesafe.config.ConfigFactory


/**
  * Demonstrate routing in a clustered environment
  * -pool routers: which create their own children, and deploy them remotely on the other nodes
  * -group router: fetch already created actors and use them as routees, have to  identify actors from remote nodes
  */


case class SimpleTask(contents: String)

case object StartWork

class MasterWithRouter extends Actor with ActorLogging {
  val router = context.actorOf(FromConfig.props(Props[SimpleRoutee]), "clusterAwareRouter") //make it cluster aware

  override def receive: Receive = {
    case StartWork =>
      log.info(s"Starting work...")
      (1 to 100) foreach { id =>
        router ! SimpleTask(s"Simple task: $id")
      }

  }
}

class SimpleRoutee extends Actor with ActorLogging {
  override def receive: Receive = {
    case SimpleTask(contents) =>
      log.info(s"Processing: $contents")
  }
}

//Getting routees up
object ClusterAwareRouteesApp extends App {
  def startRoutees(port: Int) = {
    val config = ConfigFactory.parseString(
      s"""
         |akka.remote.artery.canonical.port = $port
         |""".stripMargin
    ).withFallback(ConfigFactory.load("clustering/cluster_aware_routers.conf"))
    ActorSystem("ClusterAwareRouters", config)
  }

  startRoutees(2551)
  startRoutees(2552)
}

//for group routees
object ClusterAwareGroupRouteesApp extends App {
  def startRoutees(port: Int) = {
    val config = ConfigFactory.parseString(
      s"""
         |akka.remote.artery.canonical.port = $port
         |""".stripMargin
    ).withFallback(ConfigFactory.load("clustering/cluster_aware_routers.conf"))
    val system = ActorSystem("ClusterAwareRouters", config)
    system.actorOf(Props[SimpleRoutee], "worker")
  }

  startRoutees(2551)
  startRoutees(2552)
}

object MasterWithRouterApp extends App {
  val mainConfig = ConfigFactory.load("clustering/cluster_aware_routers.conf")
  val config = mainConfig.getConfig("masterWithRouterApp").withFallback(mainConfig)

  val system = ActorSystem("ClusterAwareRouters", config)
  val masterActor = system.actorOf(Props[MasterWithRouter], "master")

  Thread.sleep(10000)
  masterActor ! StartWork
}

// for group routees
object MasterWithGroupRouterApp extends App {
  val mainConfig = ConfigFactory.load("clustering/cluster_aware_routers.conf")
  val config = mainConfig.getConfig("masterWithGroupRouterApp").withFallback(mainConfig)

  val system = ActorSystem("ClusterAwareRouters", config)
  val masterActor = system.actorOf(Props[MasterWithRouter], "master")

  Thread.sleep(10000)
  masterActor ! StartWork
}