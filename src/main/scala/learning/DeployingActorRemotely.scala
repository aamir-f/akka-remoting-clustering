package learning

import akka.actor.{Actor, ActorLogging, ActorSystem, Address, AddressFromURIString, Deploy, PoisonPill, Props, Terminated}
import akka.remote.RemoteScope
import akka.routing.FromConfig
import com.typesafe.config.ConfigFactory

/**
  * So, name of the actor is checked in config for remote deployment,
  * if absent in config, it will be created locally
  * if present in config, then props will be passed to remote actor system,
  * the actor will be created in remote actor system and its actorRef is returned back.
  * For this remote deployment, Props object needs to be serializable and the actor class
  * need to be in remote JVM's class path, if not there, again it will be created locally
  */
object DeployingActorRemotely_Local_Application_1 extends App {
  val system = ActorSystem("LocalActorSystem", ConfigFactory.load("remoting/deploying_actors_remotely.conf").getConfig("localApp"))
  val simpleActor = system.actorOf(Props[SimpleActor], "remoteActor") // user/remoteActor
  simpleActor.tell("hello, remote Actor!", Actor.noSender)
  println("====")
  println(s"full actorRef: $simpleActor") //Actor[akka://RemoteActorSystem@localhost:2552/remote/akka/LocalActorSystem@localhost:2551/user/remoteActor#-9202370]
  println(s"actorRef path: ${simpleActor.path}") //akka://RemoteActorSystem@localhost:2552/remote/akka/LocalActorSystem@localhost:2551/user/remoteActor

  println("====")

  //Programmatically remote deployment
  val remoteASAddress: Address = AddressFromURIString("akka://RemoteActorSystem@localhost:2552")
  val remotelyDeployedActor = system.actorOf(
    Props[SimpleActor].withDeploy(
      Deploy(scope = RemoteScope(remoteASAddress)),
    ),
    "remotelyDeployedActor"
  )

  remotelyDeployedActor ! "hello, programmatically deployed remote actor"

}

object DeployingActorRemotely_Local_Application_2 extends App {
  val system = ActorSystem("LocalActorSystem", ConfigFactory.load("remoting/deploying_actors_remotely.conf").getConfig("localApp"))
  val simpleActor = system.actorOf(Props[SimpleActor], "remoteActor") // user/remoteActor

  //Router with routees deployed remotely
  /**
    * A Pool Router: is a router that creates its own children,
    * in this case, router will also deploy its children in between these 2 JVM's
    */

  val poolRouter = system.actorOf(FromConfig.props(Props[SimpleActor]), "myRouterWithRemoteChildren")
  (1 to 10).foreach(x => poolRouter ! s"message $x")

}

/**
  * The PHI accrual failure detection:
  * actor system sends heartbeat messages once a connection is established by sending a message or deploying a remote actor.
  * If a heart beat times out, its reach score (PHI) increases.
  * If the PHI score passes a threshold, the connection is quarantined = unreachable
  * the local actor system sending Terminated messages to Death Watchers of remote actors (here death watcher is in local AS)
  * So any message sent by local AS to remote AS now goes to dead letters (mostly heartbeat messages)
  * the remote actor system must be restarted to reestablish connection.
  */

// watching remote actor

object DeployingActorRemotely_Local_Application_3 extends App {

  class ParentActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case "create"        =>
        log.info("creating remote child actor")
        val child = context.actorOf(Props[SimpleActor], "remoteChildActor")
        context.watch(child)
      case Terminated(ref) => log.warning(s"child: $ref terminated")
    }
  }
  val system = ActorSystem("LocalActorSystem",
    ConfigFactory.load("remoting/deploying_actors_remotely.conf").getConfig("localApp"))


  val parentActor = system.actorOf(Props[ParentActor], "watcher")
  parentActor.tell("create", Actor.noSender)

  Thread.sleep(1000)
 // val remoteChild = system.
   // actorSelection("akka://RemoteActorSystem@localhost:2552/remote/akka/LocalActorSystem@localhost:2551/user/watcher/remoteChildActor")
 // remoteChild.tell(PoisonPill, Actor.noSender)


}

object DeployingActorRemotely_Remote_Application extends App {
  val system = ActorSystem("RemoteActorSystem",
    ConfigFactory.load("remoting/deploying_actors_remotely.conf").getConfig("remoteApp"))
}