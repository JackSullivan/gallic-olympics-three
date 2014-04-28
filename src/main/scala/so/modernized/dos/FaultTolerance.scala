package so.modernized.dos

import akka.actor.{ActorSelection, ActorRef}
import scala.collection.mutable

/**
 * @author John Sullivan
 */
case class Heartbeat(server:String)
case object RegisterTablet
case object Registered
case class Registration(tablet:ActorRef)
case class ServerDown(newServer:ActorRef)

trait FaultTolerance extends SubclassableActor with FrontendServer {

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

  def pickServer: ActorRef = {
    context.child(allocation.keys.head).get
  }

  addReceiver{
    case Heartbeat(server) => {
      lastUpdates(server) = System.currentTimeMillis()
    }
    case RegisterTablet => {
      val serverRef = pickServer
      allocation(serverRef.path.name) += sender()
      println(serverRef.path.name)
      sender ! Registration(serverRef)
    }
  }
}
