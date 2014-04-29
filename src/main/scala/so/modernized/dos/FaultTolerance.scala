package so.modernized.dos

import akka.actor.{Cancellable, ActorSelection, ActorRef}
import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global

/**
 * @author John Sullivan
 */
case object Heartbeat
case object SendHeartbeat
case object RegisterTablet
case object InitiateScan
case class Registration(tablet:ActorRef)

trait FaultTolerance extends FrontendServer {
  private var sendHeartbeat: Cancellable = null

  addPreStart { _ =>
    sendHeartbeat = context.system.scheduler.schedule(
      0 seconds, 1 seconds,
      self, SendHeartbeat)
  }

  addPostStop { _ =>
    sendHeartbeat.cancel()
  }

  addReceiver{
    case SendHeartbeat => context.parent ! Heartbeat
  }
}

trait FaultManager extends FrontendManager {
  class ServerAllocation(val tablets:mutable.ArrayBuffer[ActorRef], var lastUpdate:Long)

  protected val allocations = new mutable.HashMap[String, ServerAllocation]()

  def deathThreshold:Long

  context.children.foreach { child =>
    allocations(child.path.name) = new ServerAllocation(new mutable.ArrayBuffer[ActorRef](), System.currentTimeMillis())
  }

  def pickServer:ActorRef = context.child(allocations.minBy(_._2.tablets.size)._1).get // the server with the fewest tablets

  def scanForDeath {
    val scanTime = System.currentTimeMillis()

    val deadServers = allocations.flatMap { case (serverName, allocation) =>
      if(scanTime - allocation.lastUpdate > deathThreshold) {
        println("Haven't heard from %s in %s millis, assuming death".format(serverName, deathThreshold))
        Some(serverName)
      } else {
        None
      }
    }

    val tabletsToReallocate = deadServers.flatMap { deadServer =>
      allocations(deadServer).tablets
    }
    deadServers.foreach(allocations.remove)
    tabletsToReallocate.foreach { tablet =>
      tablet ! Registration(context.child(allocations.minBy(_._2.tablets.size)._1).get) // re-register tablet with new server
    }
  }

  addReceiver{
    case Heartbeat => {
      allocations(sender.path.name).lastUpdate = System.currentTimeMillis()
    }
    case InitiateScan => scanForDeath
    case RegisterTablet => {
      val serverRef = pickServer
      println("Assigning tablet %s to frontend %s".format(sender().path.name, serverRef.path.name))
      allocations(serverRef.path.name).tablets += sender
      sender ! Registration(serverRef)
    }
  }
}
   /*
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
      println("Did I get in here?")
      allocation(serverRef.path.name) += sender()
      sender ! Registration(serverRef)
    }
  }
}
  */
