package learning.clustering

import akka.actor.{Actor, ActorLogging, ActorSelection, ActorSystem, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{InitialStateAsEvents, MemberEvent, MemberUp}
import com.typesafe.config.ConfigFactory

/**
  * Each person has its own application
  * each message a person writes is broadcast to all participants
  * Goal: practice subscribing to cluster membership events
  */

object ChatDomain {

  case class EnterRoom(fullAddress: String, nickName: String)

  case class UserMessage(contents: String)

  case class ChatMessage(nickName: String, contents: String)

}

object ChatActor {
  def props(nickName: String, port: Int) = Props(new ChatActor(nickName, port))
}

class ChatActor(nickName: String, port: Int) extends Actor with ActorLogging {

  import ChatDomain._

  val cluster = Cluster(context.system)

  override def preStart(): Unit = {
    cluster.subscribe(
      self,
      initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent]
    )
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  override def receive: Receive = online(Map())

  def online(chatRoom: Map[String, String]): Receive = {
    case MemberUp(member) =>
      val remoteChatActorSelection = getChatActor(member.address.toString)
      remoteChatActorSelection ! EnterRoom(s"${self.path.address}@localhost:$port", nickName)

    case EnterRoom(remoteAddress, remoteNickname) =>
      if (remoteNickname != nickName) {
        log.info(s"$remoteNickname entered the room")
        context.become(online(chatRoom + (remoteAddress -> remoteNickname)))
      }
    case UserMessage(contents)                    =>
      chatRoom foreach { case (remoteAddressAsString, remoteNickName) =>
        getChatActor(remoteAddressAsString) ! ChatMessage(remoteNickName, contents)
      }

    case ChatMessage(remoteNickName, contents) => log.info(s"[$remoteNickName]: $contents")
  }

  def getChatActor(memberAddress: String): ActorSelection =
    context.actorSelection(s"$memberAddress/user/chatActor")
}

class ChatApp(nickName: String, port: Int) extends App {

  import ChatDomain._

  val config = ConfigFactory.parseString(
    s"""
       |akka.remote.artery.canonical.port = $port
       |""".stripMargin
  ).withFallback(ConfigFactory.load("clustering/chat_room.conf"))

  val system = ActorSystem("ChatRoom", config)
  val chatActor = system.actorOf(ChatActor.props(nickName, port), "chatActor")

  scala.io.Source.stdin.getLines().foreach { line =>
    chatActor ! UserMessage(line)
  }
}

object Alice extends ChatApp("Alice", 2551)

object Bob extends ChatApp("Bob", 2552)

object Charlie extends ChatApp("Charlie", 2553)