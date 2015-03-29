package me.lightspeed7.dsug.consumer

import org.apache.commons.io.Charsets
import java.io.Serializable
import org.apache.spark.streaming.StreamingContext

object KafkaConsumer {
  import com.github.nscala_time.time.Imports.DateTime
  import com.twitter.algebird.HyperLogLogMonoid
  import kafka.serializer.StringDecoder
  import me.lightspeed7.dsug._
  import org.apache.spark.streaming.kafka.KafkaUtils
  import org.apache.spark.SparkContext
  import org.apache.spark.rdd.RDD
  import org.apache.spark.storage.StorageLevel
  import org.apache.spark.streaming.StreamingContext
  import org.apache.spark.streaming.StreamingContext.toPairDStreamFunctions
  import org.apache.spark.streaming.api.java.JavaPairReceiverInputDStream
  import scala.concurrent.duration._

  lazy val context = new SparkContext("local[4]", "logAggregator")
  lazy val streamingContext = new StreamingContext(context, Config.StreamWindowDuration)

  def start = {
    // ////////////////////////////////////////
    // we cause batching of the stream in BatchDuration seconds intervals
    // ////////////////////////////////////////
    lazy val topic = Map(Config.KafkaTopic -> 1)
    lazy val zkParams = Map(
      "zookeeper.connect" -> Config.ZkServer,
      "zookeeper.connection.timeout.ms" -> Config.ZkConnTimeout,
      "group.id" -> Config.KafkaGroupId)

    lazy val messages = KafkaUtils.createStream[String, String, StringDecoder, StringDecoder](streamingContext, zkParams, topic, StorageLevel.MEMORY_AND_DISK)

    // ////////////////////////////////////////
    // we filter out non resolved geo (unknown)
    // and map (pub, geo) -> AggLog that will
    // be reduced
    // ////////////////////////////////////////
    lazy val hyperLogLog = new HyperLogLogMonoid(12) // to count uniques

    lazy val windowStream = messages //
      .map(raw => ImpressionLog.fromJson(raw._2)) //
      //      .map(log => dumpKey(log))
      .filter(_.geo != Config.UnknownGeo) //
      .map { log =>
        val key = PublisherGeoKey(log.publisher, log.geo)
        val agg = AggregationLog(log.timestamp, log.bid, 1, hyperLogLog(log.cookie.getBytes(Charsets.UTF_8)))
        (key, agg)
      }

    lazy val byWindowAggreation = windowStream.reduceByKeyAndWindow(SparkStreaming.reduceAggregationLogs, Config.StreamWindowDuration)

    // send data to streaming sinks
    lazy val mongoDbStream = new StreamToMongoDB(Config.BatchDurationMongoDB)
    lazy val uiStream = new StreamToUI()

    uiStream.aggregateWindowStream(byWindowAggreation)
    mongoDbStream.aggregateWindowStream(byWindowAggreation)

    // ////////////////////////////////////////
    // let the streaming begin
    // ////////////////////////////////////////
    streamingContext.start()
  }

  def stop = streamingContext.stop(false)

}
