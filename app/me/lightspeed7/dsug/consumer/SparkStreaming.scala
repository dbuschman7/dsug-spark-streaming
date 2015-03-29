package me.lightspeed7.dsug.consumer

import org.apache.spark.streaming.Duration
import org.apache.spark.streaming.dstream.DStream
import me.lightspeed7.dsug.PublisherGeoKey
import me.lightspeed7.dsug.AggregationLog
import org.apache.spark.rdd.RDD
import me.lightspeed7.dsug.AggregationResult
import org.joda.time.DateTime

//
// trait to support common logic on spark streaming tasks
// //////////////////////////////////////////////////////////
trait SparkStreaming extends Serializable {

  def aggregateWindowStream(stream: DStream[(PublisherGeoKey, AggregationLog)]): Unit

}

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

class StreamToMongoDB(window: Duration) extends SparkStreaming {
  import me.lightspeed7.dsug.MongoDB

  def aggregateWindowStream(stream: DStream[(PublisherGeoKey, AggregationLog)]): Unit = {

    val aggLogs = stream.reduceByKeyAndWindow(SparkStreaming.reduceAggregationLogs _, window, window)

    aggLogs.foreachRDD(rdd => {
      rdd.foreachPartition(logs => {

        import scala.concurrent.ExecutionContext.Implicits.global

        // ConnectionPool is a static, lazily initialized pool of connections
        val coll = ConnectionPool.checkout
        var count = 0
        var keyStr: String = ""
        logs.foreach { record =>
          import me.lightspeed7.dsug.MongoDB._

          val key = record._1
          val log = record._2
          val result = AggregationResult(new DateTime(log.timestamp), key.publisher, key.geo, //
            log.imps, log.uniquesHll.estimatedSize.toInt, log.sumBids / log.imps)
          coll.save(result)
          count += 1
          keyStr = key.geo
        }
        println(s"Collecting RDD logs[MongoDB] , key = ${keyStr}, size = ${count}")
        ConnectionPool.release(coll) // return to the pool for future reuse
      })
    })
  }
}

class StreamToUI extends SparkStreaming {
  import me.lightspeed7.dsug.Actors
  import me.lightspeed7.dsug.Batch

  def aggregateWindowStream(stream: DStream[(PublisherGeoKey, AggregationLog)]): Unit = {

    // already aggregated to 1 second windows

    stream.foreachRDD({ logRdd: RDD[(PublisherGeoKey, AggregationLog)] =>
      val logs = logRdd.map {
        case (PublisherGeoKey(pub, geo), AggregationLog(timestamp, sumBids, imps, uniquesHll)) =>
          AggregationResult(new DateTime(timestamp), pub, geo, imps, uniquesHll.estimatedSize.toInt, sumBids / imps)
      }.collect()

      println(s"Collecting RDD logs[UI     ] ,size = ${logs.size}")
      Actors.statistics ! Batch(logs)
    })
  }
}
