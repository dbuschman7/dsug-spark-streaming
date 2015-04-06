package me.lightspeed7.dsug.eb

import scala.collection.mutable.Subscriber
import play.api.libs.EventSource.Event
import akka.actor.ActorRef
import me.lightspeed7.dsug.ui.`package`.MessageEvent
import akka.util.Index
import java.util.Comparator

class EventBus {
  private final val subscribers = new Index[String, ActorRef](4, new Comparator[ActorRef] {
    def compare(a: ActorRef, b: ActorRef): Int = a compareTo b
  })

  def subscribe(subscriber: ActorRef, to: String): Boolean = subscribers.put(to, subscriber)
  def unsubscribe(subscriber: ActorRef, from: String): Boolean = subscribers.remove(from, subscriber)
  def unsubscribe(subscriber: ActorRef): Unit = subscribers.removeValue(subscriber)

  def publish(event: MessageEvent): Unit = {
    val i = subscribers.valueIterator(event.channel)
    while (i.hasNext) { i.next() ! event.payload }
  }
}
