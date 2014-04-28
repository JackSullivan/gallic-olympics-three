package so.modernized.dos

import akka.actor._
import com.typesafe.config.ConfigFactory
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Await}
import scala.collection.mutable
import ExecutionContext.Implicits.global
import akka.routing.Broadcast
import scala.collection.mutable.ArrayBuffer


/**
 * @author John Sullivan
 */
case class ClientRequest(request: AnyRef)

case class DBWrite(message: AnyRef)

case class DBRequest(message: AnyRef, finalRoutee: ActorRef, serverRoutee: ActorRef)

case class DBResponse(response: AnyRef, finalRoutee: ActorRef, serverRoutee: ActorRef)

case class TimestampedResponse(timestamp: Long, response: AnyRef)

case class InvalidateEvent(event: String)

case class InvalidateTeam(team: String)

case object InvalidateCache

trait WriteMessage

/**
 * The FrontEndServer trait routes read requests from table clients (and from Cacofonix
 * to the backend DBServer process, wrapping in such a way as to preserve information about
 * both the server through which it came and the original client to route it to.
 */
object FrontendManager {
  def props(numServers: Int, cacheType: String, dbPath: ActorRef) = Props(new FrontendManager(numServers, cacheType, dbPath))
}

class FrontendManager(numServers: Int, cacheType: String, dbPath: ActorRef) extends SubclassableActor with FaultManager {

  def deathThreshold = 2000L

  (0 until numServers).foreach {
    index =>
      cacheType match {
        case "push" => {
          context.actorOf(FrontendServer.pushing(dbPath, index), s"frontend-$index")
          allocation.update(s"frontend-$index", new ArrayBuffer[ActorRef])
        }
        case "pull" => {
          context.actorOf(FrontendServer.pulling(dbPath, index), s"frontend-$index")
          allocation.update(s"frontend-$index", new ArrayBuffer[ActorRef])
        }
      }
  }

  addReceiver {
    case Broadcast(message) => context.children.foreach(_ ! message)
  }
}


trait FrontendServer extends SubclassableActor {
  def dbPath: ActorRef
}


trait CachingFrontend extends FrontendServer with SubclassableActor {
  protected val eventCache = mutable.HashMap[String, EventScore]()
  protected val medalCache = mutable.HashMap[String, MedalTally]()

  addReceiver {
    case ClientRequest(message) => {
      println("%s received ClientRequest(%s) from %s".format(context.self, message, sender()))
      message match {
        case TeamMessage(team, _) if medalCache.contains(team) => sender ! TimestampedResponse(System.currentTimeMillis(), medalCache(team))
        case EventMessage(event, _) if eventCache.contains(event) => sender ! TimestampedResponse(System.currentTimeMillis(), eventCache(event))
        case _ => dbPath ! DBRequest(message, sender(), context.self)
      }
    }
    case InvalidateEvent(event) => eventCache.remove(event)
    case InvalidateTeam(team) => medalCache.remove(team)
    case DBResponse(response, routee, _) => {
      println("%s received %s from %s, routing to %s".format(context.self, response, sender(), routee))
      response match {
        case TeamMessage(team, message) => medalCache.put(team, message.asInstanceOf[MedalTally])
        case EventMessage(event, message) => eventCache.put(event, message.asInstanceOf[EventScore])
      }
      routee ! TimestampedResponse(System.currentTimeMillis(), response)
    }
  }
}

trait PushBasedCaching extends CachingFrontend with SubclassableActor {
  addReceiver {
    case m: DBWrite =>
      dbPath ! m
      m.message match {
        case EventMessage(event, _) => context.parent ! Broadcast(InvalidateEvent(event)) //frontends.foreach(_ ! InvalidateEvent(event))
        case TeamMessage(team, _) => context.parent ! Broadcast(InvalidateTeam(team)) //frontends.foreach(_ ! InvalidateTeam(team))
      }
  }
}

trait PullBasedCaching extends CachingFrontend with SubclassableActor {
  addReceiver {
    case InvalidateCache =>
      eventCache.clear()
      medalCache.clear()
  }
}

object FrontendServer {
  def pulling(_dbPath: ActorRef, id: Int): Props = Props(new CachingFrontend with PullBasedCaching {
    val dbPath = _dbPath
  })

  def pushing(_dbPath: ActorRef, id: Int): Props = Props(new CachingFrontend with PushBasedCaching {
    val dbPath = _dbPath
  })

}

object Tester {
  def main(args: Array[String]): Unit = {
    val remote = "akka.tcp://frontend-system@127.0.0.1:2552"
    val numServers = args(1).toInt
    val cacheMode = args(2).toLowerCase

    println("REMOTE: " + remote)
    println("NUM SERVERS: " + numServers)
    println("CACHE MODE: " + cacheMode)

    assert(cacheMode == "pull" || cacheMode == "push", "You must specify push or pull as a caching mode")
    val cacheInterval = if (args(2) == "push") args(3).toInt else null.asInstanceOf[Int] // FYI We want this NullPointer Exception if we get it

    implicit val timeout = Timeout(600.seconds)

    val system = ActorSystem(s"frontend-system", ConfigFactory.load("frontend.conf"))
    system.actorOf(ConcreteDB(Seq("Rome", "Gaul"), Seq("Swimming", "Tennis"), 1), "db")

    val db = Await.result(system.actorSelection(remote + "/user/db").resolveOne(), 600.seconds)

    val frontend = system.actorOf(FrontendManager.props(numServers, cacheMode, db), "frontend-manager") // todo make possible to move to multiple machines

    if (cacheMode == "pull") {
      system.scheduler.schedule(3.seconds, cacheInterval.seconds)(() => {
        frontend ! Broadcast(InvalidateCache)
      })
    }

    val client1 = new TabletClient(AddressFromURIString(remote))
    client1.register(frontend)
  }
}
/*
object FrontendProcess {
  def main(args:Array[String]) {
    val remote = args(0)
    val id = args(1).toInt

    implicit val timeout = Timeout(600.seconds)

    val system = ActorSystem(s"frontend-$id", ConfigFactory.load(s"clientserver$id"))

    val db = Await.result(system.actorSelection(remote + "/user/db").resolveOne(), 600.seconds)

    val frontend = system.actorOf(ConcreteFrontend(db, id), "frontend")
  }
}
  */

