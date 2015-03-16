// package default

import play.api.Application
import play.api.GlobalSettings
import me.lightspeed7.dsug._
import me.lightspeed7.dsug.consumer.KafkaConsumer
import scala.util.Try

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    Kafka.createTopic(Constants.KafkaTopic)
    //    MongoDB.start
    Spark.start
    KafkaConsumer.start
    Actors.start
  }

  override def onStop(app: Application) {
    Try(Actors.stop).failed.map { t => println(t.getMessage) }
    Try(KafkaConsumer.stop).failed.map { t => println(t.getMessage) }
    Try(Spark.stop).failed.map { t => println(t.getMessage) }
  }

}
