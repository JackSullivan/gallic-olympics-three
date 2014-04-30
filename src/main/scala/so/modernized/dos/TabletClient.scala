package so.modernized.dos

import akka.actor._
import akka.util.Timeout
import akka.pattern.ask
import com.typesafe.config.ConfigFactory
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

/**
 * @author John Sullivan
 */
class TabletClient(remoteAddress: Address) {

  implicit val timeout = Timeout(600.seconds)

  val system = ActorSystem("client", ConfigFactory.load("client"))
  val remote = remoteAddress.toString

  def shutdown() {system.shutdown()}

  println(s"Connecting to remote server at $remote")

  private val actor = system.actorOf(Props[TabletActor], "tablet-actor")

  // This line registers the actor with a frontend server
  Await.result(system.actorSelection(remote + "/user/frontend").resolveOne(), 600.seconds).tell(RegisterTablet, actor)

  var actorReady = false

  var counter = 0
  while (!actorReady) {
    actorReady = Await.result(actor ? Ready, 600.seconds).asInstanceOf[Boolean]
    Thread.sleep(200)
    counter += 1
    if(counter == 5) {
      counter = 0
      Await.result(system.actorSelection(remote + "/user/frontend").resolveOne(), 600.seconds).tell(RegisterTablet, actor)
    }
  }

  def getScore(event:String) {
    actor ! EventMessage(event, GetEventScore(System.currentTimeMillis()))
  }
  def getMedalTally(team:String) {
    actor ! TeamMessage(team, GetMedalTally(System.currentTimeMillis()))
  }
}

class TabletActor extends AssignableClient {
  addReceiver{
    case TimestampedResponse(timestamp, response) => response match {
      case EventScore(event, score, initTime) => {
        val latency = (System.currentTimeMillis() - initTime)/1000.0
        println("Event: %s, Score: %s. Timestamped %d. Response took %.2f secs".format(event, score, timestamp, latency))
      }
      case MedalTally(team, gold, silver, bronze, initTime) => {
        val latency = (System.currentTimeMillis() - initTime)/1000.0
        println("Team: %s, Gold: %s, Silver: %s, Bronze: %s. Timestamped %d. Response took %.2f secs".format(team, gold, silver, bronze, timestamp, latency))
      }
      case UnknownEvent(eventName, initTime) => {
        val latency = (System.currentTimeMillis() - initTime)/1000.0

        println("There are not %s competitions at these olympics. Timestamped %d. Response took %.2f secs".format(eventName, timestamp, latency))
      }
      case UnknownTeam(teamName, initTime) => {
        val latency = (System.currentTimeMillis() - initTime)/1000.0
        println("%s is not participating in these olympics. Timestamped %d. Response took %.2f secs".format(teamName, timestamp, latency))
      }
    }
    case em: EventMessage=> {println("SERVER PATH: " + server.path); println(em); server ! ClientRequest(em)}
    case tm:TeamMessage => server ! ClientRequest(tm)
  }
}

object TabletClient {
  def randomTabletClient(teams:IndexedSeq[String], events:IndexedSeq[String], address:Address, freq:Long, times:Int = 20)(implicit rand:Random) {
    def sample(strs:IndexedSeq[String]):String = strs(rand.nextInt(strs.size))

    val tablet = new TabletClient(address)
    (0 to times).foreach { _ =>
      Thread.sleep(freq)
      rand.nextInt(2) match {
        case 0 => {
          val event = sample(events)
          println("I just asked about the score for %s".format(event))
          tablet.getScore(event)
        }
        case 1 => {
          val team = sample(teams)
          println("I just asked about the medal tally for %s".format(team))
          tablet.getMedalTally(team)
        }
      }
    }
  }
}