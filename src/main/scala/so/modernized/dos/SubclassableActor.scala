package so.modernized.dos

import akka.actor.Actor
import scala.collection.mutable

/**
 * @author John Sullivan
 */
trait SubclassableActor extends Actor {

  private var receivers:mutable.ArrayBuffer[Actor.Receive] = new mutable.ArrayBuffer[Actor.Receive]()

  def addReceiver(rec:Actor.Receive) {
    receivers += rec
  }

  final def receive:Actor.Receive = receivers.reduce(_ orElse _)
}
