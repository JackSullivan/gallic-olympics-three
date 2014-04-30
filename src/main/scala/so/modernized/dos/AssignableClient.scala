package so.modernized.dos

import akka.actor.ActorRef
import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global

/**
 * @author John Sullivan
 */
case object Ready

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
