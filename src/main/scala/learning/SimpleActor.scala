package learning

import akka.actor.{Actor, ActorLogging, Props}

class SimpleActor extends Actor with ActorLogging {
  override def receive: Receive = {
    case m =>
      //val child = context.actorOf(Props[ChildActorOfSimpleActor], "ChildOfSimpleActor")
     // child ! "yo what'z up child"
      log.info(s"====Received message : $m,  from actor_name: ${sender()}====")
  }
}

class ChildActorOfSimpleActor extends Actor with ActorLogging {
  override def receive: Receive = {
    case s =>
      log.info(s"From Child of Simple Actor: $s")
  }
}
