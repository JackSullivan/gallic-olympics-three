package so.modernized.dos

import akka.actor.{ActorSystem, AddressFromURIString, Props, ActorRef}
import scala.concurrent.Await
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import scala.util.Random
import akka.pattern.ask

/**
 * @author John Sullivan
 */
class ConcretePushFrontendManager(val numServers:Int, val dbPath:ActorRef, val deathThreshold:Long) extends FrontendManager with FaultManager {

  (0 until numServers).foreach { index =>
    context.actorOf(ConcretePushFrontend(dbPath), s"frontend-$index")
  }
  println("After creation, my children: %s".format(context.children.map(_.path.name).mkString(",")))

  def childProps: Props = ConcretePushFrontend(dbPath)
}

object ConcretePushFrontendManager {
  def apply(numServers:Int, dbPath:ActorRef, deathThreshold:Long) = Props(new ConcretePushFrontendManager(numServers, dbPath, deathThreshold))
}

class ConcretePushFrontend(val dbPath:ActorRef) extends PushBasedCaching with FaultTolerance

object ConcretePushFrontend {
  def apply(dbPath:ActorRef) = Props(new ConcretePushFrontend(dbPath))
}

class ConcretePullFrontendManager(val numServers:Int, val dbPath:ActorRef, val deathThreshold:Long, stalenessRate:Int) extends FrontendManager with FaultManager {

  def childProps = ConcretePullFrontend(dbPath, stalenessRate)

  (0 until numServers).foreach { index =>
    context.actorOf(ConcretePullFrontend(dbPath, stalenessRate), s"frontend-$index")
  }
  println("After creation, my children: %s".format(context.children.map(_.path.name).mkString(",")))
}
object ConcretePullFrontendManager {
  def apply(numServers:Int, dbPath:ActorRef, deathThreshold:Long, stalenessRate:Int) = Props(new ConcretePullFrontendManager(numServers, dbPath, deathThreshold, stalenessRate))
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
    val deathThreshold = args(3).toLong
    val killRate = args(4).toLong

    println("REMOTE: " + remote)
    println("NUM SERVERS: " + numServers)
    println("CACHE MODE: " + cacheMode)
    println("Death Threshold: " + deathThreshold)
    println("Kill Rate:" + killRate)

    assert(cacheMode == "pull" || cacheMode == "push", "You must specify push or pull as a caching mode")
    val cacheInterval = if (args(2) == "pull") args(5).toInt else null.asInstanceOf[Int] // FYI We want this NullPointer Exception if we get it

    implicit val timeout = Timeout(600.seconds)

    val system = ActorSystem("frontend-system", ConfigFactory.load("frontend"))

    val db = Await.result(system.actorSelection(remote + "/user/db").resolveOne(), 600.seconds)

    val frontendProps = if(cacheMode == "pull") {
      ConcretePullFrontendManager(numServers, db, deathThreshold, cacheInterval)
    } else if(cacheMode == "push") {
      ConcretePushFrontendManager(numServers, db, deathThreshold)
    } else {
      throw new IllegalArgumentException("Invalid cache mode: %s".format(cacheMode))
    }

    val frontend = system.actorOf(frontendProps, "frontend")


    if(killRate != 0) {
      while(true) {
        println("Killing at rate: %d".format(killRate))
        Thread.sleep(killRate)
        val children = Await.result(frontend ? GetChildren, 600.seconds).asInstanceOf[List[ActorRef]]
        if(children.nonEmpty) {
          val toKill = Random.nextInt(children.size)
          println("Got children: %s".format(children))
          println("Killing %s".format(children(toKill).path.name))
          system.stop(children(toKill))

        }
      }
    }
  }
}