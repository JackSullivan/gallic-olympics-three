package so.modernized.dos

import akka.actor.{Inbox, ActorSystem, Props, ActorRef}
import com.typesafe.config.ConfigFactory

/**
 * @author John Sullivan
 *
 * The DBServer routes reads and writes coming in from the front end to appropriate teams and events.
 */
trait DBServer extends SubclassableActor {
  def teams:ActorRef
  def events:ActorRef

  addReceiver{
    case DBWrite(write) => write match {
      case tm:TeamMessage => teams ! DBWrite(write)
      case em:EventMessage => events ! DBWrite(write)
    }
    case DBRequest(request, routee, server) => request match {
      case tm:TeamMessage => teams ! DBRequest(tm, routee, server)
      case em:EventMessage => events ! DBRequest(em, routee, server)
    }
    case DBResponse(response, routee, serverRoutee) => serverRoutee ! DBResponse(response, routee, serverRoutee)
  }
}

object ConcreteDB {
  def apply(teamNames:Iterable[String], eventNames:Iterable[String]) = Props(new ConcreteDB(teamNames, eventNames))
}

class ConcreteDB(teamNames:Iterable[String], eventNames:Iterable[String]) extends DBServer {
  val teams = context.actorOf(TeamRoster(teamNames))
  val events = context.actorOf(EventRoster(eventNames))
}

object DBProcess {

  def main(args:Array[String]) {
    val teams = args(0).split('|')
    val events = args(1).split('|')

    val system = ActorSystem("db", ConfigFactory.load("db"))

    val db = system.actorOf(ConcreteDB(teams, events), "db")

  }
}