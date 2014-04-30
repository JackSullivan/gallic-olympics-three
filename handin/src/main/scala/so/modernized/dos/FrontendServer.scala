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

case object GetChildren

trait WriteMessage

/**
 * The FrontendManager handles creation of individual frontend servers, and routes broadcast messages.
 */
trait FrontendManager extends SubclassableActor {
  def numServers:Int
  def dbPath:ActorRef
  addReceiver {
    case Broadcast(message) => context.children.foreach(_ ! message)
    case GetChildren => sender ! context.children.toList
  }

}

trait FrontendServer extends SubclassableActor {
  def dbPath: ActorRef
}

/**
 * The CachingFrontend stores cache information for either a pull or push based caching system,
 * and handles loading to and serving from the cache based on client requests.
 */
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
        case MedalTally(team, g, s, b, time) => medalCache.put(team, MedalTally(team, g, s, b, time))
        case EventScore(eventName, score, time) => eventCache.put(eventName, EventScore(eventName, score, time))
      }
      routee ! TimestampedResponse(System.currentTimeMillis(), response)
    }
  }
}

/**
 * PushBasedCaching extends a CachingFrontend with a system to invalidate messages as
 * new information comes in.
 */
trait PushBasedCaching extends CachingFrontend with SubclassableActor {
  addReceiver {
    case CacofonixUpdate(message) =>
      dbPath ! DBWrite(message)
      message match {
        case EventMessage(event, _) => context.parent ! Broadcast(InvalidateEvent(event))
        case TeamMessage(team, _) => context.parent ! Broadcast(InvalidateTeam(team))
      }
  }
}

/**
 * PullBasedCaching extends a CachingFrontent with a system to periodically empty the cache
 * at a predefined stateness rate to ensure that out-of-date information is not served.
 */
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
    case CacofonixUpdate(message) => dbPath ! DBWrite(message)
  }
}