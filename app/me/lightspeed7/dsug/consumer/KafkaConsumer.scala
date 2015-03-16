package me.lightspeed7.dsug.consumer

import org.apache.commons.io.Charsets
import java.io.Serializable

object KafkaConsumer {

  lazy val ImpressionLogConsumer: Thread = init

  def start = ImpressionLogConsumer
  def stop = ImpressionLogConsumer.interrupt()

  // dedicate a specific thread to running this task.
  private def init = {
    val thread = new Thread(new KafkaConsumer)
    thread.setDaemon(false)
    thread.start()
    thread
  }

}

class KafkaConsumer extends Runnable with Serializable {
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

  def run {

    // ////////////////////////////////////////
    // we cause batching of the stream in BatchDuration seconds intervals
    // ////////////////////////////////////////
    val streamingContext = new StreamingContext(Spark.context, Constants.BatchDuration)
    val topic = Map(Constants.KafkaTopic -> 1)
    val messages = KafkaUtils.createStream[String, String, StringDecoder, StringDecoder](streamingContext, Kafka.zkParams, topic, StorageLevel.MEMORY_AND_DISK)

    // ////////////////////////////////////////
    // we filter out non resolved geo (unknown)
    // and map (pub, geo) -> AggLog that will
    // be reduced
    // ////////////////////////////////////////
    lazy val hyperLogLog = new HyperLogLogMonoid(12) // to count uniques

    def dumpKey(log: ImpressionLog): ImpressionLog = {
      println(s"Log ${log}")
      log
    }

    val logsByPubGeo = messages //
      .map(raw => ImpressionLog.fromJson(raw._2)) //
      //      .map(log => dumpKey(log))
      .filter(_.geo != Constants.UnknownGeo) //
      .map {
        log =>
          val key = PublisherGeoKey(log.publisher, log.geo)
          //          println("key = ${key}")
          val agg = AggregationLog(
            timestamp = log.timestamp,
            sumBids = log.bid,
            imps = 1,
            uniquesHll = hyperLogLog(log.cookie.getBytes(Charsets.UTF_8)))
          (key, agg)
      }

    // ////////////////////////////////////////
    // Reduce to generate imps, uniques,
    // sumBid per pub and geo per interval
    // of BatchDuration seconds
    // ////////////////////////////////////////
    def reduceAggregationLogs(aggLog1: AggregationLog, aggLog2: AggregationLog) = {
      aggLog1.copy(
        timestamp = math.min(aggLog1.timestamp, aggLog2.timestamp),
        sumBids = aggLog1.sumBids + aggLog2.sumBids,
        imps = aggLog1.imps + aggLog2.imps,
        uniquesHll = aggLog1.uniquesHll + aggLog2.uniquesHll)
    }

    //    import org.apache.spark.streaming.StreamingContext._
    val aggLogs = logsByPubGeo.reduceByKeyAndWindow(reduceAggregationLogs, Constants.BatchDuration)

    // ////////////////////////////////////////
    // for each aggregation, store in MongoDB
    // ////////////////////////////////////////
    aggLogs.foreachRDD(saveLogs(_))

    def saveLogs(logRdd: RDD[(PublisherGeoKey, AggregationLog)]) {
      val logs = logRdd.map {
        case (PublisherGeoKey(pub, geo), AggregationLog(timestamp, sumBids, imps, uniquesHll)) =>
          AggregationResult(new DateTime(timestamp), pub, geo, imps, uniquesHll.estimatedSize.toInt, sumBids / imps)
      }.collect()

      println(s"Collecting RDD logs ,size = ${logs.size}")
      logs.foreach(MongoDB.save(_)) // use batch API
    }

    // ////////////////////////////////////////
    // let the streaming begin
    // ////////////////////////////////////////
    streamingContext.start()
  }

}
