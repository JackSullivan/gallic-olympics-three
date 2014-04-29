package so.modernized.dos

import akka.actor._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.collection.mutable
import ExecutionContext.Implicits.global
import akka.routing.Broadcast


/**
 * @author John Sullivan
 */
case class CacofonixUpdate(request: AnyRef)

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
trait FrontendManager extends SubclassableActor {
  def numServers:Int
  def dbPath:ActorRef
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
    case CacofonixUpdate(message) => {
      println("%s received CacofonixUpdate(%s) from %s".format(context.self, message, sender()))
      dbPath ! DBWrite(message)
    }
    case InvalidateEvent(event) => eventCache.remove(event)
    case InvalidateTeam(team) => medalCache.remove(team)
    case DBResponse(response, routee, _) => {
      println("%s received %s from %s, routing to %s".format(context.self, response, sender(), routee))
      response match {
        case MedalTally(team, g, s, b, time) => medalCache.put(team, MedalTally(team, g, s, b, time))
        case EventScore(eventName, score, time) => eventCache.put(eventName, EventScore(eventName, score, time))
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
        case EventMessage(event, _) => context.parent ! Broadcast(InvalidateEvent(event))
        case TeamMessage(team, _) => context.parent ! Broadcast(InvalidateTeam(team))
      }
  }
}

trait PullBasedCaching extends CachingFrontend with SubclassableActor {
  private var cacheExpiration = null.asInstanceOf[Cancellable]

  def stalenessRate:Int

  addPreStart{ _ =>
    cacheExpiration = context.system.scheduler.schedule(stalenessRate.seconds, stalenessRate.seconds, self, InvalidateCache)
  }

  addPostStop{ _ =>
    cacheExpiration.cancel()
  }

  addReceiver {
    case InvalidateCache =>
      eventCache.clear()
      medalCache.clear()
    case m:DBWrite => dbPath ! m
  }
}