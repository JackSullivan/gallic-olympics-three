package so.modernized.dos

import akka.actor._
import com.typesafe.config.ConfigFactory
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.util.Timeout

/**
 * This client takes an address that corresponds to the
 * location of an olympics server and allows a cacofonix
 * user to send write messages to the teams and events
 * on that server.
 */
class CacofonixClient(remoteAddress: Address) {

  def this(olympics: Olympics) = this(Address("akka.tcp","olympics", "127.0.0.1",2552))

  implicit val timeout = Timeout(600.seconds)

  val system = ActorSystem("client", ConfigFactory.load("cacofonix.conf"))
  val remote = remoteAddress.toString
  val remoteName = "/user/frontend-manager/frontend-1"

  println(s"Cacofonix connecting to remote server at $remote$remoteName")

  def shutdown() {system.shutdown()}

  private val server = Await.result(system.actorSelection(remote + remoteName).resolveOne(), 600.seconds)
  private val listener = system.actorOf(Props[CacofonixActor], "cacofonix-actor") //todo resolve nonsense here

  def setScore(event:String, score:String) {
    listener ! EventMessage(event, SetEventScore(score, System.currentTimeMillis()))
  }

  def incrementMedalTally(team:String, medalType:MedalType) {
    listener ! TeamMessage(team, IncrementMedals(medalType, System.currentTimeMillis()))
  }

  def register(server: ActorRef) = {
    println("Registering with " + server.path)
    server.tell(RegisterTablet, listener)
  }
}

class CacofonixActor extends Actor {
  var server = null.asInstanceOf[ActorRef]

  def receive: Actor.Receive = {

//    case ServerDown(newServer) => {
//      implicit val t = Timeout(600.seconds)
//      Await.result(newServer ? RegisterTablet, 600.seconds).asInstanceOf[Registered.type]
//      server = newServer
//    }
    case em: EventMessage=> server ! CacofonixUpdate(em)
    case tm:TeamMessage => server ! CacofonixUpdate(tm)
    case Registration(serverRef) => server = serverRef
  }
}
