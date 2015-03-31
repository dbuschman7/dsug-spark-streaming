package me.lightspeed7.dsug.ui

import akka.actor.{ Actor, ActorRef, ActorSystem, PoisonPill, Props, actorRef2Scala }
import akka.event.{ ActorEventBus, LookupClassification }
import java.util.concurrent.atomic.AtomicLong
import play.Play
import play.api.libs.iteratee.Concurrent
import play.api.libs.json.{ JsValue, Json }
import play.api.libs.concurrent.Akka
import scala.collection.JavaConversions.asScalaIterator
import me.lightspeed7.dsug._
import play.api.libs.json.JsNumber
import play.api.Logger

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
        Logger.debug(s"Payload to client ${p}")
        out.push(Json.toJson(p)) // Pushing messages to Channel
      }
    }

    override def preStart() {
      super.preStart()
      EventBus.subscribe(self, "payload")
      Logger.info(s"Listener Ready - ${name}, path - ${self.path}")

    }

    override def postStop() {

      EventBus.unsubscribe(self, "payload")
      super.postStop()
      Logger.info(s"Listener ShutDown - ${name}, path - ${self.path}")
    }
  }

  //
  // Statistics Actor
  // /////////////////////////////////////
  class StatisticsActor(name: String) extends Actor {

    def receive = {
      case Counts(key, value) => EventBus.publish(MessageEvent("payload", Payload(JsNumber(value), key)))

      case Batch(logs) => {
        self ! Counts("pubGeoCount", logs.size) // handle the counts

        // first, geo impressions
        val geoImpsCounts: List[Count] = logs.groupBy(_.geo).map { c =>
          val key = c._1
          val value = c._2.foldLeft(0)((sum, ar) => sum + ar.imps)
          //          println(s"Key = ${}key}  Value = ${value}")
          Count(key, value)
        }.toList
        //        println(s"GeoImpCounts = ${geoImpsCounts}")
        EventBus.publish(MessageEvent("payload", Payload(Json.toJson(geoImpsCounts), "geoImpsCounts")))

        // second, average bids
        //        var ts: Long = System.currentTimeMillis

        val geoAvgBidsCounts: List[Count] = logs.groupBy(_.geo).map { c =>
          val key = c._1
          val value = (c._2.foldLeft(0.0)((sum, ar) => sum + ar.avgBids) * 100 / logs.size).toInt
          //          ts = c._2.foldLeft(ts)((prev, cur) => prev min cur.date.getMillis)
          Count(key, value)
        }.toList

        //        println(s"geoAvgBidsCounts = ${geoAvgBidsCounts}")
        val tsc = TimeSeriesCount(System.currentTimeMillis() / 1000, geoAvgBidsCounts)
        EventBus.publish(MessageEvent("payload", Payload(Json.toJson(tsc), "geoAvgBids")))
      }
    }

    override def preStart() {
      super.preStart()
      Logger.info(s"StatisticsActor Ready - ${name}, path - ${self.path}")

    }

    override def postStop() {
      super.postStop()
      Logger.info(s"StatisticsActor ShutDown - ${name}, path - ${self.path}")
    }
  }
}
