package me.lightspeed7.dsug

import com.twitter.algebird.HLL
import org.joda.time.DateTime
import play.api.libs.json._

// key for spark streaming
case class PublisherGeoKey(publisher: String, geo: String)

// data  for spark streaming
case class ImpressionLog(timestamp: Long, publisher: String, advertiser: String, website: String, geo: String, bid: Double, cookie: String)

object ImpressionLog {
  implicit val impressionLogFormat = Json.format[ImpressionLog]
  def toJson(log: ImpressionLog): JsValue = Json.toJson(log)
  def fromJson(v: String): ImpressionLog = {
    impressionLogFormat.reads(Json.parse(v)) match {
      case JsSuccess(l, _) => l
      case JsError(Seq(path, messages)) => {
        throw new Exception(messages._2.map(f => f.message).mkString)
      }
    }
  }
}

// intermediate result used in reducer
case class AggregationLog(timestamp: Long, sumBids: Double, imps: Int = 1, uniquesHll: HLL)

// result to be stored in MongoDB
case class AggregationResult(date: DateTime, publisher: String, geo: String, imps: Int, uniques: Int, avgBids: Double)

//
// Payload object sent to Angular
// //////////////////////////////
case class Payload(data: JsValue, target: String)
object Payload {
  implicit val payloadFormat = Json.format[Payload]
}
