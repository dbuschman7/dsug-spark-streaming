package me.lightspeed7.dsug.generator

import scala.concurrent.duration._
import akka.actor.{ Actor, ActorRef, ActorSystem }
import me.lightspeed7.dsug.{ Actors, Constants }
import scala.util.Random
import me.lightspeed7.dsug.ImpressionLog
import kafka.producer.KeyedMessage
import me.lightspeed7.dsug.Producers

class GeneratorActor(name: String) extends Actor {

  private val Tick = "tick" // scheduler tick

  implicit val ec = Actors.system.dispatcher

  private val cancellable: akka.actor.Cancellable = //
    Actors.system.scheduler.schedule(5 seconds, Constants.LogGeneratorDelay, Actors.generator, Tick)

  private val random = new Random()

  private var i = 0L

  import me.lightspeed7.dsug.Constants._
  def receive = {
    case Tick => {

      val timestamp = System.currentTimeMillis()
      val publisher = Publishers(random.nextInt(NumPublishers))
      val advertiser = Advertisers(random.nextInt(NumAdvertisers))
      val website = s"website_${random.nextInt(Constants.NumWebsites)}.com"
      val cookie = s"cookie_${random.nextInt(Constants.NumCookies)}"
      val geo = Geos(random.nextInt(Geos.size))
      val bid = math.abs(random.nextDouble()) % 1
      val log = ImpressionLog(timestamp, publisher, advertiser, website, geo, bid, cookie)

      //      Producers.producer.send(new KeyedMessage[String, ImpressionLog](Constants.KafkaTopic, log))

      i = i + 1
      if (i % 100 == 0) {
        println(s"Sent $i messages!")
      }
    }
  }

  override def preStart() {
    super.preStart()
    println("GeneratorActor Ready")
  }

  override def postStop() {
    if (cancellable != null) {
      cancellable.cancel
    }
    super.postStop()
    println("GeneratorActor")
  }
}
