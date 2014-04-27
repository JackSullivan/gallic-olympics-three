package so.modernized.dos

import akka.actor.{PoisonPill, Props, Actor, ActorSystem}
import com.typesafe.config.ConfigFactory

/**
 * Governing Olympics class that stores the actor system.
 */
class Olympics(teams:Iterable[String], events:Iterable[String]) {

  val system = ActorSystem("olympics", ConfigFactory.load("server"))

  system.actorOf(TeamRoster(teams), "teams")
  system.actorOf(EventRoster(events), "events")
  system.actorOf(Props[CacofonixListener], "cacofonix")
  system.actorOf(Props[TabletRequestRouter], "router")
  system.actorOf(EventSubscriptions(events), "subscriberRoster")

  def shutdown() {
    system.actorSelection("teams") ! PoisonPill
    system.actorSelection("events") ! PoisonPill
    system.actorSelection("cacofonix") ! PoisonPill
    system.actorSelection("router") ! PoisonPill
    system.actorSelection("subscriberRoster") ! PoisonPill
    system.shutdown()
  }

}

/**
 * Listener class to receive messages from the Cacofonix process and route
 * them to the appropriate Roster
 */
class CacofonixListener extends Actor {
  val teamPath = context.system.actorSelection(context.system./("teams"))
  val eventPath = context.system.actorSelection(context.system./("events"))

  def receive: Actor.Receive = {
    case teamMessage:TeamMessage => {
      println("cacofonix listener received a team message: %s" format teamMessage)
      teamPath ! teamMessage
    }
    case eventMessage:EventMessage => {
      println("cacofonix listener received an event message: %s" format eventMessage)
      eventPath ! eventMessage
    }
  }
}