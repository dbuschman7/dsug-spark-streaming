package me.lightspeed7.dsug.ui

import akka.actor.{ Actor, ActorRef, ActorSystem, PoisonPill, Props, actorRef2Scala }
import akka.event.{ ActorEventBus, LookupClassification }
import java.util.concurrent.atomic.AtomicLong
import play.Play
import play.api.libs.iteratee.Concurrent
import play.api.libs.json.{ JsValue, Json }
import play.api.libs.concurrent.Akka
import scala.collection.JavaConversions.asScalaIterator
import me.lightspeed7.dsug.Payload
import me.lightspeed7.dsug.Count
import me.lightspeed7.dsug.Batch

object `package` {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  private val conf = Play.application().configuration()

  //
  // Internal Actor State Classes
  // ///////////////////////////////
  case class Start(out: Concurrent.Channel[JsValue])
  case class MessageEvent(channel: String, payload: Payload)

  //
  // Message Bus
  // ////////////////////////////////
  private val EventBus = new LookupEventBus

  class LookupEventBus extends ActorEventBus with LookupClassification {
    type Event = MessageEvent
    type Classifier = String

    protected def mapSize(): Int = 4

    protected def classify(event: Event): Classifier = {
      event.channel
    }

    protected def publish(event: Event, subscriber: ActorRef): Unit = {
      subscriber ! event.payload
    }
  }

  //
  // Socket Listeners - ephemeral ( session )
  // ///////////////////////////////
  class Listener(name: String, out: Concurrent.Channel[JsValue]) extends Actor {

    def receive = {
      case p: Payload => {
        println(s"Payload to client ${p}")
        out.push(Json.toJson(p)) // Pushing messages to Channel
      }
    }

    override def preStart() {
      super.preStart()
      EventBus.subscribe(self, "payload")
      println(s"Listener Ready - ${name}, path - ${self.path}")

    }

    override def postStop() {

      EventBus.unsubscribe(self, "payload")
      super.postStop()
      println(s"Listener ShutDown - ${name}, path - ${self.path}")
    }
  }

  //
  // Statistics Actor
  // /////////////////////////////////////
  class StatisticsActor(name: String) extends Actor {

    def receive = {
      case Batch(logs) => {
        // first, geo impressions
        val geoImpsCounts: List[Count] = logs.groupBy(_.geo).map { c =>
          val key = c._1
          val value = c._2.foldLeft(0)((sum, ar) => sum + ar.imps)
          Count(key, value)
        }.toList
        EventBus.publish(MessageEvent("payload", Payload(Json.toJson(geoImpsCounts), "geoImpsCounts")))

        // second, average bids
        val geoAvgBidsCounts: List[Count] = logs.groupBy(_.geo).map { c =>
          val key = c._1
          val value = (c._2.foldLeft(0.0)((sum, ar) => sum + ar.avgBids) * 100 / logs.size).toInt
          Count(key, value)
        }.toList

        EventBus.publish(MessageEvent("payload", Payload(Json.toJson(geoAvgBidsCounts), "geoAvgBids")))
      }
    }

    override def preStart() {
      super.preStart()
      println(s"StatisticsActor Ready - ${name}, path - ${self.path}")

    }

    override def postStop() {
      super.postStop()
      println(s"StatisticsActor ShutDown - ${name}, path - ${self.path}")
    }
  }
}
