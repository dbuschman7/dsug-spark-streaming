package me.lightspeed7.dsug

import scala.concurrent.duration._
import org.apache.spark.streaming.Seconds

object Constants {

  // Generator Config
  val NumPublishers = 5
  val NumAdvertisers = 3

  val Publishers = (0 to NumPublishers).map("publisher_" +)
  val Advertisers = (0 to NumAdvertisers).map("advertiser_" +)
  val UnknownGeo = "unknown"
  val Geos = Seq("NY", "CA", "FL", "CO", "HI", UnknownGeo)
  val NumWebsites = 10000
  val NumCookies = 10000

  val LogGeneratorDelay = 10 milliseconds

  // In between
  val KafkaServer = "127.0.0.1:9092"
  val KafkaTopic = "adnetwork-topic"
  def KafkaGroupId = "dsug-spark-streaming"
  val ZkServer = "127.0.0.1:2181"
  val ZkConnTimeout = "10000"
  val ZkTimeout = 5000

  // Aggregation Config
  val MongodbServerList = List("localhost:27017")
  val MongodbDatabase = "adlogdb"
  val MongodbCollection = "impsPerPubGeo"

  val BatchDuration = Seconds(10)

}
