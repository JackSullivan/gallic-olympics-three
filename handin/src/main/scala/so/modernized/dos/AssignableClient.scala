package so.modernized.dos

import akka.actor.ActorRef
import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global

/**
 * @author John Sullivan
 */
case object Ready

/**
 * An assignable client is a client that can update the server to which it sends messages by receiving a registration message
 */
trait AssignableClient extends SubclassableActor{
  var server = null.asInstanceOf[ActorRef]

  addReceiver{
    case Registration(serverRef) => {
      println("I just go reassigned to %s".format(serverRef.path.name))
      server = serverRef
    }
    case Ready => {
      sender ! (server != null)
    }
  }
}
