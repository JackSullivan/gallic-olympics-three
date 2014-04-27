package so.modernized.dos

import akka.actor.{Props, Actor}

trait EventMessageType {
  def initTime:Long
}
case class SetEventScore(newScore:String, initTime:Long) extends EventMessageType with WriteMessage
case class GetEventScore(initTime:Long) extends EventMessageType

case class EventMessage(eventName:String, message:EventMessageType)

case class EventScore(eventName:String, score:String, initTime:Long)

case class UnknownEvent(eventName: String, initTime:Long)


/**
 * An event simply stores a score and responds to requests for it.
 * The event roster handles the logic of assigning requests to the
 * correct message. When it receives a score update it forwards the
 * update to the appropriate EventSubscription record.
 */
class Event(val name:String) {
  private var score:String = ""

  def setScore(newScore:String) {
    score = newScore
  }
  def getScore(initTime:Long) = EventScore(name, score, initTime)
}

/**
 * The event roster serves as parents to all of the events at the olympics
 * and routes incoming requests to read and write to the appropriate event.
 * If no event is found for a given request a message is sent back to the
 * requester to that effect.
 */
object EventRoster {
  def apply(eventNames:Iterable[String]):Props = Props(new EventRoster(eventNames))
}

class EventRoster(eventNames:Iterable[String]) extends Actor {

  val events = eventNames.map(name => name -> new Event(name)).toMap

  def receive: Actor.Receive = {
    case DBWrite(EventMessage(eventName, SetEventScore(newScore, _))) => events.get(eventName) match {
      case Some(event) => {
        println("Updated %s score to %s".format(event.name, newScore))
        event.setScore(newScore)
      }
      case None => println("Received invalid score update from Cacofonix: %s".format(eventName))
    }
    case DBRequest(EventMessage(eventName, message), routee, server) => events.get(eventName) match {
      case Some(event) => message match {
        case GetEventScore(initTime) => sender ! DBResponse(event.getScore(initTime), routee, server)
        //case SetEventScore(newScore, _) => event.setScore(newScore)
      }
      case None => sender ! DBResponse(UnknownEvent(eventName, message.initTime), routee, server)
    }
  }
}

