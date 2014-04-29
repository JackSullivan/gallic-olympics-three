package so.modernized.dos

import akka.actor.Actor
import scala.collection.mutable

/**
 * @author John Sullivan
 */
trait SubclassableActor extends Actor {

  private var receivers:mutable.ArrayBuffer[Actor.Receive] = new mutable.ArrayBuffer[Actor.Receive]()
  private var preStarts = new mutable.ArrayBuffer[Unit => Unit]()
  private var postStops = new mutable.ArrayBuffer[Unit => Unit]()

  def addPreStart(s:Unit => Unit) {
    preStarts += s
  }

  def addPostStop(s:Unit => Unit) {
    postStops += s
  }

  def addReceiver(rec:Actor.Receive) {
    receivers += rec
  }

  final def receive:Actor.Receive = receivers.reduce(_ orElse _)

  final override def postStop(): Unit = postStops.foreach(_.apply())

  final override def preStart(): Unit = preStarts.foreach(_.apply())
}
