package learning

import akka.actor.{Actor, ActorLogging}

class SimpleActor extends Actor with ActorLogging {
  override def receive: Receive = {
    case m => log.info(s"====Received message : $m,  from actor_name: ${sender()}====")
  }
}
