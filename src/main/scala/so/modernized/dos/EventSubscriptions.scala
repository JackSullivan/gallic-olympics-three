package so.modernized.dos

import akka.actor.{ActorRef, Actor, Props}
import scala.collection.mutable

case class Subscribe(eventName:String, initTime:Long)


/**
 * EventSubscription stores references to all of the clients
 * that subscribe to a given event and forwards event score
 * updates to them when it receives them.
 */
object EventSubscription {
  def props(eventName:String):Props = Props(new EventSubscription(eventName))
}

class EventSubscription(eventName:String) extends Actor {
  val subscribers = new mutable.ArrayBuffer[ActorRef]

  def receive: Actor.Receive = {
    case Subscribe(_, _) => subscribers += sender()
    case score:EventScore => subscribers.foreach { subscriber =>
    subscriber ! score
    }
  }
}

/**
 * EventSubscriptions receives subscribe requests for events and
 * routes them, along with the original sender to the appropriate
 * EventSubscription.
 */
object EventSubscriptions {
  def apply(events:Iterable[String]):Props = Props(new EventSubscriptions(events))
}

class EventSubscriptions(events:Iterable[String]) extends Actor {

  events.foreach { event =>
    context.actorOf(EventSubscription.props(event), event)
  }

  def receive: Actor.Receive = {
    case Subscribe(eventName, initTime) => context.child(eventName) match {
      case Some(event) => event.tell(Subscribe(eventName, initTime), sender)
      case None => sender ! UnknownEvent(eventName, initTime)
    }
  }
}