package learning

import akka.actor.{Actor, ActorIdentity, ActorLogging, ActorSelection, ActorSystem, Identify, Props}
import com.typesafe.config.ConfigFactory
import akka.util.Timeout

import scala.concurrent.duration._
import scala.util.{Failure, Success}
object RemoteActors1 extends App {

  val localActorSystem = ActorSystem("LocalSystem", ConfigFactory.load("remoting/remote_actors.conf"))
  val remoteActorSystem = ActorSystem("RemoteSystem", ConfigFactory.load("remoting/remote_actors.conf").getConfig("remote_actor_system"))

  val inLocalActorSystem_SimpleActor = localActorSystem.actorOf(Props[SimpleActor], "local_as_simple_actor")
  val inRemoteActorSystem_SimpleActor = remoteActorSystem.actorOf(Props[SimpleActor], "remote_as_simple_actor")

  inLocalActorSystem_SimpleActor ! "hello, local actor inside local as!"
  inRemoteActorSystem_SimpleActor ! "hello, remote actor inside remote as!"

}

object Local_JVM1_AS_Fetching_Remote_AS_Actor_1 extends App {
  /**
     *  send a message to Remote Simple actor running on different JVM
     * Method 1: actor selection
    */
    val localSystem = ActorSystem("LocalSystem", ConfigFactory.load("remoting/remote_actors"))
    val remoteSelection = localSystem.actorSelection("akka://Remote_AS_JVM@localhost:2552/user/remote_as_jvm_simple_actor")
    remoteSelection ! "hello from the \"local\" JVM"
}

object Local_JVM1_AS_Fetching_Remote_AS_Actor_2 extends App {
  /**
    *  send a message to Remote Simple actor running on different JVM
    * Method 2: resolve the actor selection to an actor ref
    */
  val localSystem = ActorSystem("LocalSystem", ConfigFactory.load("remoting/remote_actors"))
  val remoteActorSelection: ActorSelection = localSystem.actorSelection("akka://Remote_AS_JVM@localhost:2552/user/remote_as_jvm_simple_actor")

  implicit val timeOut = Timeout(2.seconds)
  import localSystem.dispatcher
  remoteActorSelection.resolveOne().onComplete {
    case Success(actorRef) => actorRef ! "From Local JVM, I've resolved you in a future!"
    case Failure(ex) => println(s"I failed to resolve the remote actor because: $ex")
  }
}

object Local_JVM1_AS_Fetching_Remote_AS_Actor_3 extends App {

  /**
    *  send a message to Remote Simple actor running on different JVM
    * Method 3: via actor identification protocol
    */
  class ActorResolver extends Actor with ActorLogging {


    override def preStart(): Unit = {
      super.preStart()
      val selection = localSystem.actorSelection("akka://Remote_AS_JVM@localhost:2552/user/remote_as_jvm_simple_actor")
      selection ! Identify(42)
    }
    def receive: Receive = {
      case ActorIdentity(42, Some(actorRef)) =>
        actorRef ! "Thank you for identifying yourself!"
    }
  }

  val localSystem = ActorSystem("LocalSystem", ConfigFactory.load("remoting/remote_actors"))
  val actorResolverRef = localSystem.actorOf(Props[ActorResolver], "localActorResolver")

}


/**
  *From Daniel:
  *  The remote actor receives the message after the guardian actor system receives it.
  *  Because there is no sender in the original message, the guardian just adds (its local) deadLetters actor
  *  as the sender, which explains why you're seeing this log.
  */
object RemoteActor_Separate_JVM2 extends App {
  val remote_as_jvm = ActorSystem("Remote_AS_JVM", ConfigFactory.load("remoting/remote_actors.conf").getConfig("remote_actor_system"))
  val remote_as_jvm_simple_actor = remote_as_jvm.actorOf(Props[SimpleActor], "remote_as_jvm_simple_actor")
  remote_as_jvm_simple_actor ! "hello, remote_as_jvm_simple_actor"
}