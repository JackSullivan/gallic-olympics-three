package so.modernized.dos

import akka.actor.Address
import scala.util.Random

/**
 * @author John Sullivan
 */
object SpawnRandomTablet {
  def main(args:Array[String]) {
    val add = args(0)
    val port = args(1).toInt
    val freq = args(2).toLong
    val times = args(3).toInt
    val teams = args(4).split('|')
    val events = args(5).split('|')

    TabletClient.randomTabletClient(teams, events, Address("akka.tcp", "router", add, port), freq, times)(Random)
  }
}