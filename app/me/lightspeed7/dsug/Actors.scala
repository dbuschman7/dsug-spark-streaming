package me.lightspeed7.dsug

import play.libs.Akka
import akka.actor.ActorRef
import akka.actor.Props
import me.lightspeed7.dsug.generator.GeneratorActor
import kafka.producer.ProducerConfig
import kafka.producer.Producer
import java.util.Properties

object Actors {
  private[dsug] lazy val system = Akka.system()

  lazy val generator: ActorRef = system.actorOf(Props(classOf[GeneratorActor], "GeneratorActor"))

}

object Producers {
  import scala.collection.JavaConversions._

  private[dsug] val props = new Properties()
  props ++= Map(
    "serializer.class" -> "com.chimpler.sparkstreaminglogaggregation.ImpressionLogEncoder",
    "metadata.broker.list" -> "127.0.0.1:9093")

  private[dsug] val config = new ProducerConfig(props)
  val producer = new Producer[String, ImpressionLog](config)

}
