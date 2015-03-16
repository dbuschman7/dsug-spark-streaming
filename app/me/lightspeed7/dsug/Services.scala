package me.lightspeed7.dsug

import scala.concurrent.ExecutionContext.Implicits.global

object Actors {
  import play.libs.Akka
  import akka.actor.ActorRef
  import akka.actor.Props
  import me.lightspeed7.dsug.generator.GeneratorActor

  private[dsug] lazy val system = Akka.system()

  lazy val generator: ActorRef = system.actorOf(Props(classOf[GeneratorActor], "GeneratorActor"))

  def start = {
    generator
    // more here
    println("Actors - Generator started")
  }

  def stop = system.shutdown()

}

object Spark {
  import org.apache.spark.SparkContext
  import play.api.Logger

  lazy val context = new SparkContext("local[4]", "logAggregator")

  def start = {
    context;
    Logger.info(s"Spark context initialized")
  }

  def stop() = context.stop()
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

  def createProps = {
    val props = new Properties()
    props ++= Map(
      "serializer.class" -> "kafka.serializer.StringEncoder",
      "metadata.broker.list" -> Constants.KafkaServer)
    new ProducerConfig(props)
  }

  lazy val producer = {
    val producer = new Producer[String, String](createProps)
    println(s"Kafka producer started")
    producer
  }

  val zkParams = Map(
    "zookeeper.connect" -> Constants.ZkServer,
    "zookeeper.connection.timeout.ms" -> Constants.ZkConnTimeout,
    "group.id" -> Constants.KafkaGroupId)

  private[this] def withZooKeeper[T](f: (ZkClient) => T): Try[T] = {
    val client = new ZkClient(Constants.ZkServer, Constants.ZkTimeout, Constants.ZkTimeout, ZKStringSerializer)
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

      val one = Try(TopicCommand.createTopic(client, createOption(1)))
      val two = one orElse Try(TopicCommand.createTopic(client, createOption(0)))
      two recoverWith {
        case _: TopicExistsException =>
          Logger.info(s"Topic ${topicName} already exists")
          Try(Unit)
        case error: Exception => Failure(error)
      }
    }
  }
}

object MongoDB {
  import me.lightspeed7.dsug.{ AggregationResult, Constants }
  import org.joda.time.DateTime

  import reactivemongo.api.MongoDriver
  import reactivemongo.api._
  import reactivemongo.api.collections.default.BSONCollection
  import reactivemongo.bson._

  import scala.concurrent.Await
  import scala.concurrent.duration._

  object MongoConversions {
    implicit object DateTimeHandler extends BSONHandler[BSONDateTime, DateTime] {
      def read(t: BSONDateTime) = new DateTime(t.value)

      def write(t: DateTime) = BSONDateTime(t.getMillis)
    }
  }

  def save(result: AggregationResult) = {
    collection.save(result)
  }

  def start = {
    Await.result(connection.waitForPrimary(20 seconds), 25 seconds)

    val stats = Await.result(collection.stats(), 5 seconds)
    println(s"MongoDB collection size = ${stats.count}")
  }

  // setup
  lazy val driver = new MongoDriver
  lazy val connection = driver.connection(Constants.MongodbServerList)

  implicit object DateTimeHandler extends BSONHandler[BSONDateTime, DateTime] {
    def read(t: BSONDateTime) = new DateTime(t.value)

    def write(t: DateTime) = BSONDateTime(t.getMillis)
  }

  implicit val aggHandler = Macros.handler[AggregationResult]

  lazy val db = connection.db(Constants.MongodbDatabase)
  lazy val collection = db[BSONCollection](Constants.MongodbCollection)

}
