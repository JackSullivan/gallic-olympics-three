package so.modernized.dos

import akka.actor.{Props, Actor}

trait TeamMessageType {
  def initTime:Long
}
case class IncrementMedals(medalType:MedalType, initTime:Long) extends TeamMessageType with WriteMessage
case class GetMedalTally(initTime:Long) extends TeamMessageType

case class TeamMessage(teamName:String, message:TeamMessageType)
case class UnknownTeam(teamName:String, initTime:Long)

case class MedalTally(team:String, gold:Int, silver:Int, bronze:Int, initTime:Long)

/**
 * The team object stores medal counts and responds to requests to
 * read and write to them. It relies on TeamRoster to ensure that
 * it receive the appropriate messages.
 */
class Team(val name:String) {
  private var gold:Int = 0
  private var silver:Int = 0
  private var bronze:Int = 0

  def tally(initTime:Long) = MedalTally(name, gold, silver, bronze, initTime)

  def increment(mType:MedalType) {
    mType match {
      case Gold => gold += 1
      case Silver => silver += 1
      case Bronze => bronze += 1
    }
  }
}

/**
 * The team roster serves as parents to all of the teams at the olympics
 * and routes incoming requests to read and write to the appropriate team.
 * If no team is found for a given request a message is sent back to the
 * requester to that effect.
 */
object TeamRoster{
  def apply(teams:Iterable[String]):Props = Props(new TeamRoster(teams))
}

class TeamRoster(teamNames:Iterable[String]) extends Actor {

  val teams = teamNames.map(name => name -> new Team(name)).toMap

  override def receive: Actor.Receive = {
    case DBWrite(TeamMessage(teamName, IncrementMedals(medalType, initTime))) => teams.get(teamName) match {
      case Some(team) => {
        println("Updated %s Medal count by %s".format(team.name, medalType.toString))
        team.increment(medalType)
      }
      case None => println("Received invalid score update from Cacofonix: %s".format(teamName))
    }
    case DBRequest(TeamMessage(teamName, message), routee, server) => teams.get(teamName) match {
        case Some(team) => message match {
          case GetMedalTally(initTime) => sender() ! DBResponse(team.tally(initTime), routee, server)
          //case IncrementMedals(medal, _) => team.increment(medal)
        }
        case None => sender() ! DBResponse(UnknownTeam(teamName, message.initTime), routee, server)
    }
  }
}