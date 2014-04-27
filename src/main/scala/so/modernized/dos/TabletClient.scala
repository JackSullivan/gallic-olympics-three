package so.modernized.dos

import akka.actor.{Address, Props, Actor, ActorSystem}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

/**
 * @author John Sullivan
 */
class TabletClient(remoteAddress: Address) {

  def this(olympics: Olympics) = {
    this(Address("akka.tcp","olympics", "127.0.0.1",2552))
  }

  implicit val timeout = Timeout(600.seconds)

  val system = ActorSystem("client", ConfigFactory.load("client"))
  val remote = remoteAddress.toString

  def shutdown() {system.shutdown()}

  println(s"Connecting to remote server at $remote")

  private val router = Await.result(system.actorSelection(remote + "/user/router").resolveOne(), 600.seconds)
  private val printer = system.actorOf(Props[TabletPrinter])

  def getScore(event:String) {
    router.tell(EventMessage(event, GetEventScore(System.currentTimeMillis())), printer)
  }
  def getMedalTally(team:String) {
    router.tell(TeamMessage(team, GetMedalTally(System.currentTimeMillis())), printer)
  }
}

class TabletPrinter extends Actor {
  def receive: Actor.Receive = {
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
  }
}

object Remote {
  def main(args:Array[String]) {
    val a = Address("akka","olympics", "127.0.0.1",2552)
    println(a.toString)
  }
}

object OlympicServer {
  def main(args: Array[String]) {
    val olympics = new Olympics(Seq("Gaul", "Rome", "Carthage", "Pritannia", "Lacadaemon"), Seq("Curling", "Biathlon", "Piathlon"))
    println("Let the games begin!")

//    olympics.shutdown()
  }
}

object TabletClient {
  def main(args:Array[String]) {
    val olympics = new Olympics(Seq("Gaul", "Rome", "Carthage", "Pritannia", "Lacadaemon"), Seq("Curling", "Biathlon", "Piathlon"))

    val client = new TabletClient(olympics)

    val cacofonix = new CacofonixClient(olympics)

    cacofonix.setScore("Curling", "Gaul 1, Rome 2, Carthage 0")
    cacofonix.incrementMedalTally("Lacadaemon", Gold)

    cacofonix.setScore("Curling", "Gaul 2, Rome 2, Carthage 0")
    cacofonix.setScore("Curling", "Gaul 3, Rome 2, Carthage 0")
    cacofonix.setScore("Curling", "Gaul 3, Rome 2, Carthage 1")

    client.getMedalTally("Gaul")
    client.getMedalTally("Lacadaemon")
    client.getScore("Curling")

//    Thread.sleep(5000)
//    client.shutdown()
//    olympics.shutdown()
  }

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