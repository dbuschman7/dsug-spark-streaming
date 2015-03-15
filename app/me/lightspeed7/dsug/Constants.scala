package me.lightspeed7.dsug

import scala.concurrent.duration._

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
  val KafkaServer = "127.0.0.1:9093"
  val KafkaTopic = "adnetwork-topic"

  // Aggregation Config
  val MongodbServerList = List("localhost")
  val MongodbDatabase = "adlogdb"
  val MongodbCollection = "impsPerPubGeo"

}
