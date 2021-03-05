package learning.advanced

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.typesafe.config.ConfigFactory

import java.util.UUID
import scala.util.Random

/**
  * Feature allows us to ensure a single instance of an actor running
  * in a cluster
  * The cluster singleton pattern is implemented by akka.cluster.singleton.ClusterSingletonManager.
  * It manages one singleton actor instance among all cluster nodes or a group of nodes tagged
  * with a specific role. ClusterSingletonManager is an actor that is supposed to be started
  * as early as possible on all nodes, or all nodes with specified role, in the cluster. The actual singleton
  * actor (is a child of CLusterSingletonManager) is started by the ClusterSingletonManager on the oldest node
  * by creating a child actor from supplied Props. ClusterSingletonManager makes sure that at most
  * one singleton instance is running at any point in time.
  * This actual actor is moved to a new oldest running node, if the current one leaves (handover)
  *
  * You can access the singleton actor by using the provided akka.cluster.singleton.ClusterSingletonProxy,
  * which will route all messages to the current instance of the singleton. The proxy will keep track
  * of the oldest node in the cluster and resolve the singleton’s ActorRef by explicitly
  * sending the singleton’s actorSelection the akka.actor.Identify message and waiting for it to reply.
  * This is performed periodically if the singleton does’t reply within a certain (configurable) time.
  * Given the implementation, there might be periods of time during which the ActorRef is unavailable,
  * e.g., when a node leaves the cluster. In these cases, the proxy will buffer the messages
  * sent to the singleton and then deliver them when the singleton is finally available.
  * If the buffer is full the ClusterSingletonProxy will drop old messages when new messages
  * are sent via the proxy. The size of the buffer is configurable and it can be disabled by
  * using a buffer size of 0.
  *
  * For some use cases it is convenient and sometimes also mandatory to ensure that you have exactly
  * one actor of a certain type running somewhere in the cluster.
  *
  * Some examples:
  * -single point of responsibility for certain cluster-wide consistent decisions,
  * or coordination of actions across the cluster system
  * -single entry point to an external system
  * -single master, many workers
  * -centralized naming service, or routing logic
  * Using a singleton should not be the first design choice. It has several drawbacks,
  * such as single-point of bottleneck (meaning it can introduce latency in ours systems if we are dealing with high volume requests).
  * Single-point of failure is also a relevant concern,
  * but for some cases this feature (ClusterSingletonManager) takes care of that by making sure that another singleton instance will
  * eventually be started.
  *
  * Make sure to not use a Cluster downing strategy that may split the cluster into several separate clusters
  * in case of network problems or system overload (long GC pauses), since that will result in
  * multiple Singletons being started, one in each separate cluster!
  *
  * Singletons being good for "maintaining a well-defined transaction ordering"
  * Singletons and sharded entities are usually coupled with persistence, as their state is lost during migration.
  *
  * The cluster failure detector will notice when oldest node becomes unreachable due to things like JVM crash,
     hard shut down, or network failure. After Downing and removing that node, a new oldest node will take over
      and a new singleton actor is created. For these failure scenarios there will not be a graceful hand-over,
      but more than one active singletons is prevented by all reasonable means. Some corner cases are eventually
      resolved by configurable timeouts. Additional safety can be added by using a Lease.
  */

case class Order(items: List[String], total: Double)

case class Transaction(orderId: Int, txnId: String, amount: Double)

class PaymentSystem extends Actor with ActorLogging {
  override def receive: Receive = {
    case t: Transaction => log.info(s"Validating transaction: $t")
    case m              => log.info(s"Received unknown message: $m")
  }
}

class PaymentSystemNode(port: Int) extends App {
  val config = ConfigFactory.parseString(
    s"""
       |akka.remote.artery.canonical.port = $port
       |""".stripMargin
  ).withFallback(ConfigFactory.load("advanced/cluster_singleton.conf"))
  val system = ActorSystem("RTJVMCluster", config)
  system.actorOf(
    ClusterSingletonManager.props(
      singletonProps = Props[PaymentSystem],
      terminationMessage = PoisonPill,
      ClusterSingletonManagerSettings(system)
    ),
    "paymentSystem"
  )
} // one node in my cluster, so 3 nodes on my cluster

object Node1 extends PaymentSystemNode(2551)

object Node2 extends PaymentSystemNode(2552)

object Node3 extends PaymentSystemNode(2553)

//client to talk to this singleton actor

class OnlineShopCheckout(paymentSystemProxy: ActorRef) extends Actor with ActorLogging {

  var orderId = 0

  override def receive: Receive = {

    case Order(_, totalAmount) =>
      log.info(s"Received order: $orderId for amount: $totalAmount, sending transaction to validate ")
      val newTransaction = Transaction(orderId, UUID.randomUUID().toString, totalAmount)
      paymentSystemProxy ! newTransaction
      orderId += 1
  }
}

object OnlineShopCheckout {
  def apply(paymentSystem: ActorRef) = Props(new OnlineShopCheckout(paymentSystem))
}

object PaymentSystemClient extends App {
  val config = ConfigFactory.parseString(
    s"""
       |akka.remote.artery.canonical.port = 0
       |""".stripMargin
  ).withFallback(ConfigFactory.load("advanced/cluster_singleton.conf"))
  val system = ActorSystem("RTJVMCluster", config)

  val proxy: ActorRef = system.actorOf(
    ClusterSingletonProxy.props(
      singletonManagerPath = "/user/paymentSystem",
      settings = ClusterSingletonProxySettings(system)
    ),
    "paymentSystemProxy"
  )

  val onlineShopCheckout = system.actorOf(OnlineShopCheckout(proxy))

  import system.dispatcher

  import scala.concurrent.duration._

  system.scheduler.schedule(5.seconds, 1.second, () => {
    val randomOrder = Order(List(), Random.nextDouble() * 100)
    onlineShopCheckout ! randomOrder
    // Random.nextDouble() * 100: any double number from 0 to 100
  })
}
