package me.lightspeed7.dsug

import me.lightspeed7.dsug.ui.StatisticsActor
import me.lightspeed7.dsug.consumer.KafkaConsumer
import org.apache.spark.streaming.StreamingContext

object Actors {
  import play.libs.Akka
  import akka.actor.ActorRef
  import akka.actor.Props
  import me.lightspeed7.dsug.generator.GeneratorActor

  private[dsug] lazy val system = Akka.system()

  lazy val generator: ActorRef = system.actorOf(Props(classOf[GeneratorActor], "GeneratorActor"))

  lazy val statistics: ActorRef = system.actorOf(Props(classOf[StatisticsActor], "StatisticsActor"))
  def start = {
    generator
    // more here
    println("Actors - Generator started")
  }

  def stop = system.shutdown()

}

object Kafka {
  import java.util.Properties
  import kafka.admin.TopicCommand
  import kafka.common.TopicExistsException
  import kafka.producer.{ ProducerConfig, Producer }
  import kafka.serializer.StringDecoder
  import kafka.utils.{ ZkUtils, ZKStringSerializer }
  import org.apache.spark.streaming.kafka.KafkaUtils
  import org.I0Itec.zkclient.ZkClient
  import play.api.Logger
  import scala.collection.JavaConversions._
  import scala.util.{ Try, Failure, Success }

  lazy val producer = {
    val props = new Properties()
    props.put("serializer.class", "kafka.serializer.StringEncoder")
    props.put("metadata.broker.list", Config.KafkaServer)
    props.put("batch.num.messages", "25")

    val producer = new Producer[String, String](new ProducerConfig(props))
    println(s"Kafka producer started")
    producer
  }

  private[this] def withZooKeeper[T](f: ZkClient => T): Try[T] = {
    val client = new ZkClient(Config.ZkServer, Config.ZkTimeout, Config.ZkTimeout, ZKStringSerializer)
    val result = Try(f(client))
    Try(client.close) match {
      case Failure(e) => Logger.error("Unable to close ZkClient", e)
      case _          =>
    }
    result
  }

  def createTopic(topicName: String) = withZooKeeper { client =>
    val topics = ZkUtils.getAllTopics(client)
    if (topics.contains(topicName)) Logger.info(s"Kafka Topic ${topicName} already exists")
    else {

      def createOption(replicationFactor: Int = 1) = {
        val options = Array("--replication-factor", "1", "--topic", topicName, "--partitions", "1")
        new TopicCommand.TopicCommandOptions(options)
      }

      lazy val one = Try(TopicCommand.createTopic(client, createOption(1)))
      lazy val two = Try(TopicCommand.createTopic(client, createOption(0)))

      one orElse two recoverWith {
        case _: TopicExistsException =>
          Logger.info(s"Topic ${topicName} already exists")
          Try(Unit)
        case error: Exception => Failure(error)
      }
    }
  }

  def start = KafkaConsumer.start

  def stop = KafkaConsumer.stop

}

object MongoDB {
  import me.lightspeed7.dsug.{ AggregationResult, Config }
  import org.joda.time.DateTime

  import reactivemongo.api.MongoDriver
  import reactivemongo.api._
  import reactivemongo.api.collections.default.BSONCollection
  import reactivemongo.bson._

  import scala.concurrent.Await
  import scala.concurrent.duration._

  import scala.concurrent.ExecutionContext.Implicits.global

  // Reactive Mongo Helpers
  object MongoConversions {
    implicit object DateTimeHandler extends BSONHandler[BSONDateTime, DateTime] {
      def read(t: BSONDateTime) = new DateTime(t.value)

      def write(t: DateTime) = BSONDateTime(t.getMillis)
    }
  }

  implicit object DateTimeHandler extends BSONHandler[BSONDateTime, DateTime] {
    def read(t: BSONDateTime) = new DateTime(t.value)

    def write(t: DateTime) = BSONDateTime(t.getMillis)
  }

  implicit val aggHandler = Macros.handler[AggregationResult]

  // public API
  def getCollection(name: String) = db[BSONCollection](name)

  // startup and shutdown
  def start = {
    // make sure we can connect to the database before continuing
    Await.result(connection.waitForPrimary(20 seconds), 25 seconds)

    val dbName = db.name
    val collName = Config.MongodbCollection
    val stats = Await.result(getCollection(Config.MongodbCollection).stats(), 5 seconds)
    println(s"MongoDB - db = ${dbName}, collection name = ${collName}, size = ${stats.count}")
  }

  // MAKE SURE THIS IS LAZY
  private lazy val connection = new MongoDriver().connection(Config.MongodbServerList)
  private lazy val db = connection.db(Config.MongodbDatabase)

}
