// package default

import play.api.Application
import play.api.GlobalSettings
import me.lightspeed7.dsug._
import scala.util.Try

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    Kafka.createTopic(Config.KafkaTopic)
    //    Actors.start
    //    MongoDB.start
    //    Thread sleep 2000
    //    Kafka.start
  }

  override def onStop(app: Application) {
    Try(Kafka.stop).failed.map { t => println(t.getMessage) }
    Try(Actors.stop).failed.map { t => println(t.getMessage) }
  }

}
