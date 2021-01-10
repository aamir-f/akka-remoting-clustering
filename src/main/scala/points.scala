/**
  * Actor Model Principles
    --> every interaction is based on sending messages
    --> full actor encapsulation(we can't poke actor's internal state
        we can interact with it only via sending messages)
    --> no locking (actors are single threaded and they share thread-
        pools on whatever JVM they are deployed on)
    --> message-sending latency (sending and receiving takes time)
    --> Some message delivery guarantee (at most once message delivery), message might be
        lost along the way but if gets send to actor, it will get send only once
    --> message ordering maintained per sender/receiver pair

  * This principle holds:
        --> if actors are deployed on same JVM in a parallel application on same machine
        --> locally on multiple JVMs on same machine
        --> in a distributed environment of any scale on different machines
  * Akka was designed with the distributed goal in mind

  * Location Transparency: (to meet above principles)
      --> if we ask or tell, actor can be anywhere, we don't care.
      --> i.e we don't care where the object is
  * Transparent Remoting:
      --> we're using the object as if it were local
          (behind the scene it interacts with object deployed elsewhere) e.g Java-RMI
  * Location Transparency != Transparent Remoting
  */

/**
  * Akka-Remoting
  * Local v/s remote actor: only difference is in bunch of configuration changes
  * Artery is re-implementation of old remoting module:
    --> Its focused on high-throughput, low-latency communication
  */

/**
  * akka clustering allows us to build truly distributed applications.
  * They are de-centralized, peer-to-peer with no single point of failure
  * within cluster node membership is automatically detected with gossip protocol
  * failure detector, phi score detector
  * use clustering instead of remoting, way too powerful
  * So clusters = composed of nodes with host+port+uniqueId
  * these nodes can be deployed on same JVM, multliple JVM on same machine, or on
   set of machines of any scale
  * A core part of clustering is managing cluster members using gossip protocol and PHI
  * There is no leader election, leader is deterministically chosen once the gossip protocal
    converges.
  *Join a cluster
   --> contact seed nodes in order (from configuration)
  ---> first node is seed node, then other node send join commands and gets into Joining State
       and send this info throught-out the cluster, also wait for gossip to converge.
  -->all nodes in cluster must acknowledge the new node, then leader will set the state of this
     node to up and sends this info to the all nodes in cluster
  *Leave a cluster
   --> way 1. node switches its state to leaving and send info to entire cluster(gossip converge), leader set its
        state to exiting (again gossip converges), then leader marks it removed
  --> way 2. if JVM crashes, network error;
      node becomes unreachable, gossip converge and leader actions are not possible, so node must be
      downed manually
      Can also be downed by akka cluster or leader using down() method, down() is risking might make other cluster down too

  * When to use akka clustering
   --> building distributed application
      --> withing microservice nodes in akka clustering needs to be tightly coupled
  * When not to use
   --> for inter-microservice communication
   --> distributed monolith

  * Cluster sharding for scale, consistency and fail over
    --> balance resource(memory, disk space, network traffic) across multiple nodes for scalability
    --> distribute data and entities across multiple nodes in cluster
    --> Location transparency and automatic failure relocation
  --> Entity is basic unit in cluster sharding

  --> Split brain resolvers to resolve the problem of split brain in a cluster
 */