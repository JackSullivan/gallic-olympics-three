package so.modernized.dos

import akka.actor.{ActorSystem, AddressFromURIString, Props, ActorRef}
import akka.routing.Broadcast
import scala.concurrent.Await
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._

/**
 * @author John Sullivan
 */
class ConcretePushFrontendManager(val numServers:Int, val dbPath:ActorRef, val deathThreshold:Long) extends FrontendManager with FaultManager {

  (0 to numServers).foreach { index =>
    context.actorOf(ConcretePushFrontend(dbPath), s"frontend-$index")
  }
}

class ConcretePushFrontend(val dbPath:ActorRef) extends PushBasedCaching with FaultTolerance

object ConcretePushFrontend {
  def apply(dbPath:ActorRef) = Props(new ConcretePushFrontend(dbPath))
}

class ConcretePullFrontendManager(val numServers:Int, val dbPath:ActorRef, val deathThreshold:Long, stalenessRate:Int) extends FrontendManager with FaultManager {

  (0 to numServers).foreach { index =>
    context.actorOf(ConcretePullFrontend(dbPath, stalenessRate))
  }
}

class ConcretePullFrontend(val dbPath:ActorRef, val stalenessRate:Int) extends PullBasedCaching with FaultTolerance

object ConcretePullFrontend {
  def apply(dbPath:ActorRef, stalenessRate:Int) = Props(new ConcretePullFrontend(dbPath, stalenessRate))
}

object ConcreteFrontend {
  def main(args:Array[String]) {
    val remote = args(0)
    val numServers = args(1).toInt
    val cacheMode = args(2).toLowerCase

    println("REMOTE: " + remote)
    println("NUM SERVERS: " + numServers)
    println("CACHE MODE: " + cacheMode)

    assert(cacheMode == "pull" || cacheMode == "push", "You must specify push or pull as a caching mode")
    val cacheInterval = if (args(2) == "push") args(3).toInt else null.asInstanceOf[Int] // FYI We want this NullPointer Exception if we get it

    implicit val timeout = Timeout(600.seconds)

    val system = ActorSystem("frontend-system", ConfigFactory.load("frontend.conf"))
    system.actorOf(ConcreteDB(Seq("Rome", "Gaul"), Seq("Swimming", "Tennis"), 1), "db")

    val db = Await.result(system.actorSelection(remote + "/user/db").resolveOne(), 600.seconds)

    val frontendProps = if(cacheMode == "pull") {
      ConcretePullFrontend(db, cacheInterval)
    } else if(cacheMode == "push") {
      ConcretePushFrontend(db)
    } else {
      throw new IllegalArgumentException("Invalid cache mode: %s".format(cacheMode))
    }

    val frontend = system.actorOf(frontendProps, "frontend-manager") // todo make possible to move to multiple machines

    val client1 = new TabletClient(AddressFromURIString(remote))
    client1.getScore("Swimming")
    client1.getMedalTally("Rome")
  }
}