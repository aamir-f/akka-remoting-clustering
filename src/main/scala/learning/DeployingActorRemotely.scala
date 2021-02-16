package learning

import akka.actor.{Actor, ActorSystem, Props}
import com.typesafe.config.ConfigFactory

/**
  * So, name of the actor is checked in config for remote deployment,
  * if absent in config, it will be created locally
  * if present in config, then props will be passed to remote actor system,
  * the actor will be created in remote actor system and its actorRef is returned back.
  * For this remote deployment, Props object needs to be serializable and the actor class
  * need to be in remote JVM's class path, if not there, again it will be created locally
  */
object DeployingActorRemotely_Local_Application extends App {
  val system = ActorSystem("LocalActorSystem", ConfigFactory.load("remoting/deploying_actors_remotely.conf").getConfig("localApp"))
  val simpleActor = system.actorOf(Props[SimpleActor], "remoteActor") // user/remoteActor
  simpleActor.tell("hello, remote Actor!", Actor.noSender)
  println("====")
  println(simpleActor) //Actor[akka://RemoteActorSystem@localhost:2552/remote/akka/LocalActorSystem@localhost:2551/user/remoteActor#-9202370]
  println(simpleActor.path) //akka://RemoteActorSystem@localhost:2552/remote/akka/LocalActorSystem@localhost:2551/user/remoteActor

  println("====")
}

object DeployingActorRemotely_Remote_Application extends App {
  val system = ActorSystem("RemoteActorSystem", ConfigFactory.load("remoting/deploying_actors_remotely.conf").getConfig("remoteApp"))

}