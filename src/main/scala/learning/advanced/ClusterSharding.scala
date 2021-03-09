package learning.advanced

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import com.typesafe.config.ConfigFactory

import java.util.{Date, UUID}
import scala.collection.immutable
import scala.util.Random

/**
 * We will split an actor A in multiple smaller actors
 	- same type A
 	- potentially multiple instances on the same node
 	- an instance of A = entity, and it has an ID i.e entityId

 * If we want to do this sharding, then
   --> Every node starts Cluster Sharding for an actor type.
  		- every node starts a special Shard Region actor.
  		- every Shard Region is responsible for a ShardId.
  		  (it's often the case that a single Shard Region is responsible for multiple ShardIDs)
  		- a special shard coordinator which will starts as a cluster singleton in our cluster,
  		  its responsible for figuring which shardId stays on which nodes, also responsible for
  		  migrating shards between nodes.

 	--> Every message is sent to the Shard Region of the local node.
 	--> the local shard region will map (runs 2 function to identity shardId, entityId) the message to the Shard ID and an Entity ID
 	--> the local shard will ask the Shard Coordinator (singleton) for the destination node for this shardId, entityId
 	--> once this SR will receive message from SC, it will simply forward the message to the correct node and entity.
 	--> In future, any message with same shardId, will be directly handled by this SR, without asking SC again.
 	--> If entityId doesn't exist, SR will create the one of type A and route message to it.
 	--> The whole intention of this process was for a particular message M to get to an entity(smaller part of bigger actor A I sharded).

 * Other Sharding Magic
   --> Shard re-balancing
   	   - If a shard has too many entities, the entities can be migrated to other nodes.
   	     This is called a hand over process,during this handover process, messages for a shard are buffered in the responsible shard region.
   	   - the state of the entities is not migrated(same as clustersingleton exercise), best,
   	   		if we are using stateful entities, then better to make them persistent actors

 * Shard passivation
 	- if an entity is passive for a while (doesn't receive messages for a while), best to stop it to free its memory.
 	- we won't use context.stop directly to stop, we will use / entity sends Passivate message to its parent ShardRegion,
 	  then ShardRegion stops the entity.
 	  - If the SR receives the new message destined for the same entityId, SR creates new entity for it.

 	 //why not use master/routees logic, why sharding?
 * Sharding is useful when the entire state of the system is too big for a single actor and/or a single machine, so you split it into shards.
 * We aren't looking for consistency, but rather distribution of complex logic.
 *  Cluster sharding is needed when an actor is too big for a single machine.

 * The typical use case is when you have many stateful actors that together consume more resources (e.g. memory) than fit on one machine.
  * You need to distribute them across several nodes in the cluster and you want to be able to interact with them using their logical identifier,
  * but without having to care about their physical location in the cluster, which might also change over time.
  *
  *  A shard is a group of entities that will be managed together.
*/

/**
  * Modelling London Underground
    - each tube station has its own application with its own ActorSystem.
    - customer taps their Oyster card on the turnstiles.
    - the turnstile queries an OysterCardValidator to validate the entry.
    - once it replies, the turnstile will either open the gate or display an error.

   * OyesterCardValidator
     - 3 billion rides a year; needs to be shareded.
     - each station (node) will have a ShardRegion.
     - since we are sharding this OyesterCardValidator, each shardRegion will have some
       small OysterCardValidator entities as their children and  theses entities will be
       dynamically generated on a by need basis.
*/

object ClusterSharding_1 extends App {

	case class OysterCard(id: String, amount: Double)
	case class EntryAttempt(oysterCard: OysterCard, date: Date)
  case object EntryAccepted
	case class EntryRejected(reason: String)
///////////////////////////
 // Actors
//////////////////////////
  //this will be sharded
  class OysterCardValidator extends Actor with ActorLogging {
   //assume, it stores an enormous amount of data, i.e it's memory heavy say

    override def preStart(): Unit = {
      super.preStart()
      log.info("Validator Starting...")
    }

    override def receive: Receive = {
      case EntryAttempt(card @ OysterCard(id, amount), _) =>
        log.info(s"Validating $card")
        if(amount > 2.5) sender() ! EntryAccepted
        else sender() ! EntryRejected(s"[$id] not enough funds, please top up...")
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
    }
  }

  ///////////////////////////
  // Cluster nodes
  //////////////////////////

  class TubeStation(port: Int, numberOfTurnstiles: Int) extends App {

    val config = ConfigFactory.parseString(
      s"""
        |akka.remote.artery.canonical.port = $port
        |""".stripMargin
    ).withFallback(ConfigFactory.load("learning/advanced/cluster_sharding.conf"))

    val system = ActorSystem("ClusterSharding", config)

    //Setting up Cluster Sharding
    //returns reference of the shard region that is going to be deployed on this node
    val validatorShardRegionRef: ActorRef = ClusterSharding(system).start(
      typeName = "OysterCardValidator",
      entityProps = Props[OysterCardValidator],
      settings = ClusterShardingSettings(system),
      extractEntityId = TurnstileSettings.extractEntityId,
      extractShardId = TurnstileSettings.extractShardId
    )

    val turnstiles: immutable.Seq[ActorRef] = (1 to numberOfTurnstiles).map(_ => system.actorOf(Turnstile.props(validatorShardRegionRef)))
    Thread.sleep(10000) // for cluster consensus to arrive

    for(_ <- 1 to 1000) {
      val randomTurnstileIndex = Random.nextInt(numberOfTurnstiles)
      val randomTurnstile = turnstiles(randomTurnstileIndex)
      randomTurnstile ! OysterCard(UUID.randomUUID().toString, Random.nextDouble() * 10)
      Thread.sleep(200)
    }
  }
  object PiccadillyCircus extends TubeStation(2551, 10)
  object Westminster extends TubeStation(2561, 5)
  object CharingCross extends TubeStation(2571, 15)

}