package so.modernized.dos

import akka.actor.{ActorSelection, ActorRef}
import scala.collection.mutable

/**
 * @author John Sullivan
 */
case class Heartbeat(server:String)
case object RegisterTablet
case object Registered
case class Registration(tablet:ActorRef, serverName:String)
case class ServerDown(newServer:ActorRef)

trait FaultTolerance extends SubclassableActor with FrontendServer {
  addReceiver{
    case RegisterTablet => {
      context.parent ! Registration(sender, self.path.name)
      sender ! Registered
    }
  }
}

trait FaultManager extends SubclassableActor {
  protected val allocation = new mutable.HashMap[String, mutable.ArrayBuffer[ActorRef]]().withDefault(_ => new mutable.ArrayBuffer[ActorRef]())

  protected val lastUpdates = new mutable.HashMap[String, Long]()

  def deathThreshold:Long

  def scanForDeath = {
    val scanTime = System.currentTimeMillis()
    lastUpdates.foreach{ case(server, updateTime) =>
      if(scanTime - updateTime > deathThreshold) {
        println("Ding DOng the %s is dead!".format(server))
        allocation(server).foreach { tablet =>

          tablet ! ServerDown(context.child(allocation.keySet.-(server).head).get)
        }
        allocation.remove(server)
      }
    }
  }

  addReceiver{
    case Heartbeat(server) => {
      lastUpdates(server) = System.currentTimeMillis()
    }
    case Registration(tablet, serverName) => {
      println("Registered %s with %s".format(tablet.path.name, serverName))
      allocation(serverName) += tablet
    }
  }
}
