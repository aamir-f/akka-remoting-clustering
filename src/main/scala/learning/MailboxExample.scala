package learning

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}
import akka.dispatch.{ControlMessage, PriorityGenerator, UnboundedControlAwareMailbox, UnboundedPriorityMailbox}
import com.typesafe.config.{Config, ConfigFactory}

object Mailboxes extends App {

  /**
    * Interesting case #1 - custom priority mailbox
    * P0 -> most important
    * P1
    * P2
    * P3
    */

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

  // step 1 - mailbox definition
  class SupportTicketPriorityMailbox(settings: ActorSystem.Settings, config: Config)
    extends UnboundedPriorityMailbox(
      PriorityGenerator {
        case message: String if message.startsWith("[P0]") => 0 // higher priority
        case message: String if message.startsWith("[P1]") => 1
        case message: String if message.startsWith("[P2]") => 2
        case message: String if message.startsWith("[P3]") => 3
        case _                                             => 4
      }
    )


  val system = ActorSystem("MailboxDemo", ConfigFactory.load().getConfig("mailboxesDemo"))
  // step 2 - make it known in the config
  // step 3 - attach the dispatcher to an actor

  val supportTicketLogger = system.actorOf(Props[SimpleActor].withDispatcher("support-ticket-dispatcher"))
  //supportTicketLogger ! PoisonPill
  //Thread.sleep(1000)
  supportTicketLogger ! "[P3] this thing would be nice to have"
  supportTicketLogger ! "[P0] this needs to be solved NOW!"
  supportTicketLogger ! "[P1] do this when you have the time"
  supportTicketLogger ! "a normal message"
  supportTicketLogger ! "second normal message"

}