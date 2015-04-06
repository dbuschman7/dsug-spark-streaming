package me.lightspeed7.dsug.generator

import akka.actor.{ Actor, ActorRef, ActorSystem }
import kafka.producer.KeyedMessage
import me.lightspeed7.dsug._
import play.api.libs.json.Json
import scala.concurrent.duration._
import scala.util.Random
import play.api.Logger

class GeneratorActor(name: String) extends Actor {

  private val Tick = "tick" // scheduler tick

  implicit val ec = Actors.system.dispatcher

  private val cancellable: akka.actor.Cancellable = //
    Actors.system.scheduler.schedule(5 seconds, Config.LogGeneratorDelay, Actors.generator, Tick)

  private val random = new Random()

  private var i = 0L

  import me.lightspeed7.dsug.Config._
  def receive = {
    case Tick => {

      val timestamp = System.currentTimeMillis()
      val publisher = Publishers(random.nextInt(NumPublishers))
      val advertiser = Advertisers(random.nextInt(NumAdvertisers))
      val website = s"website_${random.nextInt(Config.NumWebsites)}.com"
      val cookie = s"cookie_${random.nextInt(Config.NumCookies)}"
      val geo = Geos(random.nextInt(Geos.size))
      val bid = math.abs(random.nextDouble()) % 1
      val log = ImpressionLog(timestamp, publisher, advertiser, website, geo, bid, cookie)

      val payload: String = ImpressionLog.toJson(log).toString()
      Kafka.producer.send(new KeyedMessage[String, String](Config.KafkaTopic, payload))

      i = i + 1
      if (i % 100 == 0) {
        Logger.debug(s"Sent $i messages!")
        Actors.statistics ! Counts("rawCount", 100)
      }
    }
  }

  override def preStart() {
    super.preStart()
    Logger.info("GeneratorActor Ready")
  }

  override def postStop() {
    if (cancellable != null) {
      cancellable.cancel
    }
    super.postStop()
    Logger.info("GeneratorActor")
  }
}
