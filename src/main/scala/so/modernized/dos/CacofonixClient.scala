package so.modernized.dos

import akka.actor._
import com.typesafe.config.ConfigFactory
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.util.Timeout
import scala.collection.mutable
import scala.util.Random
import akka.pattern.ask

/**
 * This client takes an address that corresponds to the
 * location of an olympics server and allows a cacofonix
 * user to send write messages to the teams and events
 * on that server.
 */
class CacofonixClient(olympicsAddress: Address) {

  def this(olympics: Olympics) = this(Address("akka.tcp","olympics", "127.0.0.1",2552))

  implicit val timeout = Timeout(600.seconds)

  val system = ActorSystem("client", ConfigFactory.load("cacofonix"))
  val remote = olympicsAddress.toString

  println(s"Connecting to remote server at $remote")

  def shutdown() {system.shutdown()}

  val actor = system.actorOf(Props[CacofonixActor])

  // This line registers the actor with a frontend server
  Await.result(system.actorSelection(remote + "/user/frontend").resolveOne(), 600.seconds).tell(RegisterTablet, actor)


  var actorReady = false

  while (!actorReady) {
    actorReady = Await.result(actor ? Ready, 600.seconds).asInstanceOf[Boolean]
    Thread.sleep(200)
  }

  def setScore(event:String, score:String) {
    actor ! DBWrite(EventMessage(event, SetEventScore(score, System.currentTimeMillis())))
  }

  def incrementMedalTally(team:String, medalType:MedalType) {
    actor ! DBWrite(TeamMessage(team, IncrementMedals(medalType, System.currentTimeMillis())))
  }
}

class CacofonixActor extends AssignableClient {
  addReceiver{
    case m:DBWrite => server ! DBWrite
  }
}

object CacofonixClient {
  def main(args: Array[String]) {
    val host = args(0)
    val port = args(1).toInt
    val teams = args(2).split('|')
    val events = args(3).split('|')

    val cacofonix = new CacofonixClient(Address("akka.tcp","router", host, port))
    val eventToScore = new mutable.HashMap[String, mutable.HashMap[String, Int]]

    events.foreach(event => eventToScore.update(event, {
      val scores = new mutable.HashMap[String, Int]
      teams.foreach(team => scores.update(team, 0))
      scores
    })
    )

    println("Cacofonix is here and ready to report on the games!")
    (0 until 100).foreach(_ => {
      val event = events(Random.nextInt(events.length))
      val team = teams(Random.nextInt(teams.length))
      val scores = eventToScore(event)

      if (Random.nextBoolean()) {
        val score = scores(team)
        scores.update(team, score + 1)
        cacofonix.setScore(event, scores.toSeq.map({ case (t,s) => t + " " + s }).mkString(", "))
        println("Cacofonix reported %s for %s".format(scores.toSeq.map({ case (t,s) => t + " " + s }).mkString(", "), event))
      } else {
        println("Cacofonix reported on a medal for %s".format(team))
        cacofonix.incrementMedalTally(team, Random.nextInt(3) match {
          case 0 => Bronze
          case 1 => Silver
          case 2 => Gold
        })
      }

      Thread.sleep(1000)
    })
  }
}