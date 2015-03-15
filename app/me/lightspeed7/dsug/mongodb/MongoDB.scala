package me.lightspeed7.dsug.mongodb

import org.apache.commons.io.Charsets
import org.joda.time.DateTime
import com.novus.salat
import com.novus.salat.global.ctx
import kafka.serializer.{ Decoder, Encoder }
import kafka.utils.VerifiableProperties
import me.lightspeed7.dsug.{ AggregationResult, ImpressionLog }
import reactivemongo.api._
import reactivemongo.api.collections.default.BSONCollection
import reactivemongo.bson._
import me.lightspeed7.dsug.Constants

object MongoDB {
  import MongoConversions._
  import scala.concurrent.ExecutionContext.Implicits.global

  // encode and decode logs in JSON (in this tuto for readability purpose) but it would be better to consider something like AVRO or protobuf)
  class ImpressionLogDecoder(props: VerifiableProperties) extends Decoder[ImpressionLog] {
    def fromBytes(bytes: Array[Byte]): ImpressionLog = {
      salat.grater[ImpressionLog].fromJSON(new String(bytes, Charsets.UTF_8))
    }
  }

  class ImpressionLogEncoder(props: VerifiableProperties) extends Encoder[ImpressionLog] {
    def toBytes(impressionLog: ImpressionLog): Array[Byte] = {
      salat.grater[ImpressionLog].toCompactJSON(impressionLog).getBytes(Charsets.UTF_8)
    }
  }

  object MongoConversions {
    implicit object DateTimeHandler extends BSONHandler[BSONDateTime, DateTime] {
      def read(t: BSONDateTime) = new DateTime(t.value)

      def write(t: DateTime) = BSONDateTime(t.getMillis)
    }
  }

  def save(result: AggregationResult) = {
    collection.save(result)
  }

  // setup
  lazy val driver = new MongoDriver
  lazy val connection = driver.connection(Constants.MongodbServerList)

  implicit val aggHandler = Macros.handler[AggregationResult]

  lazy val db = connection(Constants.MongodbDatabase)
  lazy val collection = db[BSONCollection](Constants.MongodbCollection)

}
