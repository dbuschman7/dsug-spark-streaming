package me.lightspeed7.dsug.consumer

import org.apache.spark.streaming.Duration
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.rdd.RDD
import org.joda.time.DateTime
import me.lightspeed7.dsug._
import play.api.Logger

object SparkStreaming {

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
}

class StreamToUI {
  import me.lightspeed7.dsug._

  def aggregateWindowStream(stream: DStream[(PublisherGeoKey, AggregationLog)]): Unit = {
    // already aggregated to 1 second windows
    stream.foreachRDD({ logRdd: RDD[(PublisherGeoKey, AggregationLog)] =>
      val logs = logRdd.map {
        case (PublisherGeoKey(pub, geo), AggregationLog(timestamp, sumBids, imps, uniquesHll)) =>
          AggregationResult(new DateTime(timestamp), pub, geo, imps, uniquesHll.estimatedSize.toInt, sumBids / imps)
      }.collect() // output operation

      Logger.debug(s"Collecting RDD logs[UI     ] ,size = ${logs.size}")
      Actors.statistics ! Batch(logs)
    })
  }
}

class StreamToMongoDB(window: Duration) {
  import me.lightspeed7.dsug.MongoDB
  import me.lightspeed7.dsug.MongoDB._
  import scala.concurrent.ExecutionContext.Implicits.global

  def aggregateWindowStream(stream: DStream[(PublisherGeoKey, AggregationLog)]): Unit = {
    val aggLogs = stream.reduceByKeyAndWindow(SparkStreaming.reduceAggregationLogs _, window, window)
    aggLogs.foreachRDD(rdd => {
      rdd.foreachPartition(logs => {
        lazy val coll = ConnectionPool.checkout //static, lazily initialized pool of connections
        var count = 0
        var keyStr: String = ""
        logs.foreach { record =>
          val key = record._1
          val log = record._2
          val result = AggregationResult(new DateTime(log.timestamp), key.publisher, key.geo, //
            log.imps, log.uniquesHll.estimatedSize.toInt, log.sumBids / log.imps)
          coll.save(result)
          count += 1
          keyStr = key.geo
        }
        Logger.debug(s"Collecting RDD logs[MongoDB] , key = ${keyStr}, size = ${count}")
        Actors.statistics ! Counts("mongoCount", count)
        ConnectionPool.release(coll) // return to the pool for future reuse
      })
    })
  }
}

class StreamToMongoDBConnPerRDD(window: Duration) {
  import me.lightspeed7.dsug.MongoDB
  import me.lightspeed7.dsug.MongoDB._
  import scala.concurrent.ExecutionContext.Implicits.global

  def aggregateWindowStream(stream: DStream[(PublisherGeoKey, AggregationLog)]): Unit = {
    val aggLogs = stream.reduceByKeyAndWindow(SparkStreaming.reduceAggregationLogs _, window, window)

    aggLogs.foreachRDD { logRdd: RDD[(PublisherGeoKey, AggregationLog)] =>
      val logs = logRdd.map {
        case (PublisherGeoKey(pub, geo), AggregationLog(timestamp, sumBids, imps, uniquesHll)) =>
          AggregationResult(new DateTime(timestamp), pub, geo, imps, uniquesHll.estimatedSize.toInt, sumBids / imps)
      }.collect() // output operation

      // more oo like
      val coll = ConnectionPool.checkout
      logs.foreach { log => coll.save(log) }
      ConnectionPool.release(coll)

      // functional like
      ConnectionPool.release(logs.foldLeft(ConnectionPool.checkout) {
        (coll, log) => { coll.save(log); coll }
      })
    }
  }
}
