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