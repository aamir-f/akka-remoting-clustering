package part2_remoting

import akka.actor.{Actor, ActorIdentity, ActorLogging, ActorRef, ActorSelection, ActorSystem, Identify, Props}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object RemoteActors1 extends App {
  val localSystem = ActorSystem("LocalSystem", ConfigFactory.load("part2_remoting/remoteActors.conf"))
  val remoteSystem = ActorSystem("RemoteSystem", ConfigFactory.load("part2_remoting/remoteActors.conf").getConfig("remoteSystem"))

  val localSimpleActor = localSystem.actorOf(Props[SimpleActor], "localSimpleActor")
  val remoteSimpleActor = remoteSystem.actorOf(Props[SimpleActor], "remoteSimpleActor")

  localSimpleActor ! "hello, local actor!"
  remoteSimpleActor ! "hello, remote actor!"

  //make provider = local and remove remote config for local actor, and see log will change
  // so to expose actor-system to network, we use provider=remote and file in remote configuration
}

object RemoteActors2 extends App {
  val localSystem = ActorSystem("LocalSystem", ConfigFactory.load("part2_remoting/remoteActors.conf"))

 // send a message to Remote Simple actor running on different JVM
  //Method 1: actor selection
  val remoteActorSelection = localSystem.actorSelection("akka://RemoteSystem@localhost:2552/user/remoteSimpleActor")
  remoteActorSelection ! "hello from the \"local\" JVM"
}

object RemoteActors3 extends App {
  val localSystem = ActorSystem("LocalSystem", ConfigFactory.load("part2_remoting/remoteActors.conf"))

  // Method 2: resolve the actor selection to an actor ref
  import localSystem.dispatcher
  val remoteActorSelection: ActorSelection = localSystem.actorSelection("akka://RemoteSystem@localhost:2552/user/remoteSimpleActor")
  implicit val timeout = Timeout(3.seconds)
  val remoteActorRefFuture: Future[ActorRef] = remoteActorSelection.resolveOne()
  remoteActorRefFuture.onComplete {
    case Success(actorRef) => actorRef ! "I've resolved you in a future!"
    case Failure(ex) => println(s"I failed to resolve the remote actor because: $ex")
  }
}

object RemoteActors4 extends App {

  val localSystem = ActorSystem("LocalSystem", ConfigFactory.load("part2_remoting/remoteActors.conf"))

  // Method 3: actor identification via messages or via actor identification protocol
  /*
    - actor resolver will ask for an actor selection from the local actor system
    - actor resolver will send a Identify(42) to the actor selection
    - the remote actor will AUTOMATICALLY respond with ActorIdentity(42, actorRef)
    - the actor resolver is free to use the remote actorRef
   */
  class ActorResolver extends Actor with ActorLogging {
    override def preStart(): Unit = {
      val selection = context.actorSelection("akka://RemoteSystem@localhost:2552/user/remoteSimpleActor")
      selection ! Identify(42)
    }

    override def receive: Receive = {
      case ActorIdentity(42, Some(actorRef)) =>
        actorRef ! "Thank you for identifying yourself!"
    }
  }

  localSystem.actorOf(Props[ActorResolver], "localActorResolver")
}

object RemoteActors_Remote extends App {
  val remoteSystem = ActorSystem("RemoteSystem", ConfigFactory.load("part2_remoting/remoteActors.conf").getConfig("remoteSystem"))
  val remoteSimpleActor = remoteSystem.actorOf(Props[SimpleActor], "remoteSimpleActor")
  remoteSimpleActor ! "hello, remote actor!"
}