package learning.clustering

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

object ClusterBasics extends App {

   def startCluster(ports: List[Int]) = ports foreach { port =>
     val config = ConfigFactory.parseString(
       s"""
         |akka.remote.artery.canonical.port = $port
         |""".stripMargin
     ).withFallback(ConfigFactory.load("clustering/clustering_basics.conf"))
     ActorSystem("RTJVMCluster", config)
   }

  startCluster(List(2551, 2552, 0))
}