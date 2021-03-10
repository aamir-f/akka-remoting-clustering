package learning.advanced

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion.Passivate
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import com.typesafe.config.ConfigFactory

import java.util.{Date, UUID}
import scala.collection.immutable
import scala.concurrent.duration.DurationInt
import scala.util.Random

/**
  * We will split an actor A in multiple smaller actors
  * - same type A
  * - potentially multiple instances on the same node
  * - an instance of A = entity, and it has an ID i.e entityId
  *
  * If we want to do this sharding, then
  * --> Every node starts Cluster Sharding for an actor type.
  * - every node starts a special Shard Region actor.
  * - every Shard Region is responsible for a ShardId.
  * (it's often the case that a single Shard Region is responsible for multiple ShardIDs)
  * - a special shard coordinator which will starts as a cluster singleton in our cluster,
  * its responsible for figuring which shardId stays on which nodes, also responsible for
  * migrating shards between nodes.
  *
  * --> Every message is sent to the Shard Region of the local node.
  * --> the local shard region will map (runs 2 function to identity shardId, entityId) the message to the Shard ID and an Entity ID
  * --> the local shard will ask the Shard Coordinator (singleton) for the destination node for this shardId, entityId
  * --> once this SR will receive message from SC, it will simply forward the message to the correct node and entity.
  * --> In future, any message with same shardId, will be directly handled by this SR, without asking SC again.
  * --> If entityId doesn't exist, SR will create the one of type A and route message to it.
  * --> The whole intention of this process was for a particular message M to get to an entity(smaller part of bigger actor A I sharded).
  *
  * Other Sharding Magic
  * --> Shard re-balancing
  * - If a shard has too many entities, the entities can be migrated to other nodes.
  * This is called a hand over process,during this handover process, messages for a shard are buffered in the responsible shard region.
  * - the state of the entities is not migrated(same as clustersingleton exercise), best,
  * if we are using stateful entities, then better to make them persistent actors
  *
  * Shard passivation
  * - if an entity is passive for a while (doesn't receive messages for a while), best to stop it to free its memory.
  * - we won't use context.stop directly to stop, we will use / entity sends Passivate message to its parent ShardRegion,
  * then ShardRegion stops the entity.
  * - If the SR receives the new message destined for the same entityId, SR creates new entity for it.
  *
  * //why not use master/routees logic, why sharding?
  * Sharding is useful when the entire state of the system is too big for a single actor and/or a single machine, so you split it into shards.
  * We aren't looking for consistency, but rather distribution of complex logic.
  * Cluster sharding is needed when an actor is too big for a single machine.
  *
  * The typical use case is when you have many stateful actors that together consume more resources (e.g. memory) than fit on one machine.
  * You need to distribute them across several nodes in the cluster and you want to be able to interact with them using their logical identifier,
  * but without having to care about their physical location in the cluster, which might also change over time.
  *
  * A shard is a group of entities that will be managed together.
  */

/**
  * Modelling London Underground
  * - each tube station has its own application with its own ActorSystem.
  * - customer taps their Oyster card on the turnstiles.
  * - the turnstile queries an OysterCardValidator to validate the entry.
  * - once it replies, the turnstile will either open the gate or display an error.
  *
  * OyesterCardValidator
  * - 3 billion rides a year; needs to be shareded.
  * - each station (node) will have a ShardRegion.
  * - since we are sharding this OyesterCardValidator, each shardRegion will have some
  * small OysterCardValidator entities as their children and  theses entities will be
  * dynamically generated on a by need basis.
  */

/**
  * When a node crashes:
  * - The messages will be handled by the other nodes
  * --> the coordinator will distribute the dead shards to the existing nodes.
  * --> new entities will be created on demand.
  * --> until the gossip protocol converges, messages to those shards are buffered until consensus
  * - If the Cluster Shard Coordination dies:
  * --> it's moved to another node
  * --> during this handover process, all messages to the Coordinator are buffered
  * --> all messages requiring the Coordinator (e.g "where's shard 44?") are buffered at the SR which want to contact the coordinator.
  * --> we can configure the buffer size by specifying: akka.cluster.sharding-buffer-size = 100000
  */

/**
  * Magical Features:
  * Shard re-balancing: remember entities
  * - impossible to test reliably.
  * - when a shard is re-balanced (i.e moved to another node), it can be configured programmatically to automatically
  * recreate its existing entities, instead of creating them on demand.
  * - the entity state is not recovered (unless they're persistent actors)
  * Passivation:
  * - serves idle entities which are using memory.
  * - We achieve passivation by two step process:
  * --> the entity in question, via setReceiveTimeOut scheduler or some other meaning of detecting idleness,
  * send a Passivate(YourSpecialMessage) to the parent(which is a shard region).
  * --> ShardRegion ack that special message and sends you back YourSpecialMessage and after that sends you no more messages.
  * --> that entity then handle YourSpecialMessage and stop / which serves as a signal that its safe to shut-down.
  * -
  */


case class OysterCard(id: String, amount: Double)

case class EntryAttempt(oysterCard: OysterCard, date: Date)

case object EntryAccepted

case class EntryRejected(reason: String)

//passivate message
case object TerminateValidator

///////////////////////////
// Actors
//////////////////////////
//this will be sharded
class OysterCardValidator extends Actor with ActorLogging {
  //assume, it stores an enormous amount of data, i.e it's memory heavy say


  override def preStart(): Unit = {
    super.preStart()
    log.info("Validator Starting...")
    context.setReceiveTimeout(10.seconds)
  }

  override def receive: Receive = {
    case EntryAttempt(card@OysterCard(id, amount), _) =>
      log.info(s"Validating $card")
      if (amount > 2.5) sender() ! EntryAccepted
      else sender() ! EntryRejected(s"[$id] not enough funds, please top up...")
    case ReceiveTimeout =>
      context.parent ! Passivate(TerminateValidator) //parent my shard region
    case TerminateValidator => context.stop(self) // I'm sure I won't be contacted again, so safe to stop.
  }
}

class Turnstile(validator: ActorRef) extends Actor with ActorLogging {
  override def receive: Receive = {
    case o: OysterCard => validator ! EntryAttempt(o, new Date)
    case EntryAccepted => log.info(s"GREEN: please pass")
    case EntryRejected(reason) => log.info(s"RED: $reason")
  }
}

object Turnstile {
  def props(validator: ActorRef) = Props(new Turnstile(validator))
}

///////////////////////////
// Sharding Settings
//////////////////////////

object TurnstileSettings {
  val numberOfShards = 10 //use 10x number of nodes in your cluster
  val numberOfEntities = 100 // use 10x number of shards

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case attempt@EntryAttempt(OysterCard(cardId, _), _) =>
      val entityId = cardId.hashCode.abs % numberOfEntities
      (entityId.toString, attempt)

  }

  val extractShardId: ShardRegion.ExtractShardId = {
    case EntryAttempt(OysterCard(cardId, _), _) =>
      val shardId = cardId.hashCode.abs % numberOfShards
      shardId.toString

    //remember entities
    case ShardRegion.StartEntity(entityId) =>
      //for this entityId we are supposed to return shardId that is supposed to own it.
      (entityId.toLong % numberOfShards).toString

  }
  /** ---complicated Math, if complex functions, duh--------
    * bear in mind, in practice, we have to stay in consistent with the following:
    *
    * e.g: For a message M, if entityId = 44 and shardId = 8, now, in future everytime,
    * if for a M we will get entityId = 44, shardId must also be/extracted to  8
    * so, its important that entityId, shardId pair will remain consistent throughout the application. i.e
    *
    * There must be no two messages M1, M2, for which
    * extractEntityId(M1) == extractEntityId(M2) && extractShardId(M1) != extractShardId(M2)
    *
    * If we fail to do that, i.e
    * M1 -> E37, S9
    * M2 -> E37, S10
    * then, S9, S10 on different nodes, will create this E37 twice in the cluster. THIS IS BAD.
    * if these two entity actors are also persistent actors, then they are two actors with same persistenceId which
    * will write to our journal. really bad...journal may get corrupt and unusable.
    *
    * case ShardRegion.StartEntity(entityId) => for this feature, remember entities, some additional restriction i.e
    * for a entityId1 -> ShardId1, then forAll Messages M, if extractEntityId(M) = entityId1, then extractShardId(M) MUST BE shardId1
    * otherwise same problem, because handover was there, and now shardId will be different
    */

}

///////////////////////////
// Cluster nodes
//////////////////////////

class TubeStation(port: Int, numberOfTurnstiles: Int) extends App {

  val config = ConfigFactory.parseString(
    s"""
       |akka.remote.artery.canonical.port = $port
       |""".stripMargin
  ).withFallback(ConfigFactory.load("advanced/cluster_sharding.conf"))

  val system = ActorSystem("ClusterSharding", config)

  //Setting up Cluster Sharding
  //returns reference of the shard region that is going to be deployed on this node
  val validatorShardRegionRef: ActorRef = ClusterSharding(system).start(
    typeName = "OysterCardValidator",
    entityProps = Props[OysterCardValidator],
    settings = ClusterShardingSettings(system).withRememberEntities(true), // remember entities turn-on, ShardRegion.StartEntity message will be send to the shard region when it takes responsibility of a new shard.
    extractEntityId = TurnstileSettings.extractEntityId,
    extractShardId = TurnstileSettings.extractShardId
  )

  val turnstiles: immutable.Seq[ActorRef] = (1 to numberOfTurnstiles).map(i => system.actorOf(Turnstile.props(validatorShardRegionRef), s"turnstile-${i}"))
  Thread.sleep(10000) // for cluster consensus to arrive

  for (_ <- 1 to 1000) {
    val randomTurnstileIndex = Random.nextInt(numberOfTurnstiles)
    val randomTurnstile = turnstiles(randomTurnstileIndex)
    randomTurnstile ! OysterCard(UUID.randomUUID().toString, Random.nextDouble() * 10)
    Thread.sleep(200)
  }
}

object PiccadillyCircus extends TubeStation(2551, 10)

object Westminster extends TubeStation(2561, 5)

object CharingCross extends TubeStation(2571, 15)

/**
  * Dynamically changing the number of shards is not possible. You'll have to stop and restart your cluster.If so then
    the strategy of load balancing using hash function would certainly fail in that scenario


   Q:
  Is there some best practices about how to identify the ideal number for numberOfShards and numberOfEntities? The number of nodes may change over time
   or dynamically based on the load of the application (so maybe I need to start 2x, 10x more nodes) but as far as I understood,
   I cannot change the numberOfShards and numberOfEntities values runtime to keep my extraction logic consistent.
   (And the suggestion was that those numbers should be in correlation with the number of nodes)
   In case I'm running an application in production and I came to the conclusion that those numbers are not sufficient for me,
   do I have any other options for changing them than to restart the entire cluster? (Stop all nodes, and then start all?)
  It seems to me that choosing those numbers are important, how can I be sure, that the numbers I choose would be good for me? Maybe with performance testing?

   A:
   The reason why we're using a static numberOfShards and numberOfEntities is so that we can have a simple hashing technique.
   The real goal is implementing a correct way of extracting an entity id and a shard id for the relevant messages. That's all the sharding needs.
   You can of course devise a more complex mechanism for ShardRegion.ExtractEntityId and ShardRegion.ExtractShardId that doesn't need those static values.
   That will obviously depend on the use case.

  Q:
   I want to know whether akka cluster and its features like cluster sharding supports deployment in different locations?
   For instance, one akka cluster sharding contains one sharding region in Europe and another in US?
    If possible, how to secure the messages between them, any solutions like encryption or VPN? Thanks.

  A:
   It's recommended to keep your clusters in the same region, because the latency might pose difficulties
    for the underlying protocols (e.g. leader election, sharding, consensus etc). I don't think Akka has an out-of-the-box encryption tool for messages.
  */
