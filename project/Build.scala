import sbt._
import Keys._
import sbt.Keys._
import java.io.PrintWriter
import java.io.File
import play.Play.autoImport._
//import PlayKeys._
import sys.process.stringSeqToProcess

//import com.typesafe.sbt.SbtNativePackager._
//import NativePackagerKeys._

object ApplicationBuild extends Build {

  val appName = "dsug-spark-streaming"

  val branch = "git rev-parse --abbrev-ref HEAD".!!.trim
  val commit = "git rev-parse --short HEAD".!!.trim
  val buildTime = (new java.text.SimpleDateFormat("yyyyMMdd-HHmmss")).format(new java.util.Date())

  val major = 0
  val minor = 1
  val patch = 0
  val appVersion = s"$major.$minor.$patch-$commit"

  scalaVersion := "2.10.4"

  //  val theScalaVersion = scala.util.Properties.versionString.substring(8)

  println()
  println(s"App Name      => ${appName}")
  println(s"App Version   => ${appVersion}")
  println(s"Git Branch    => ${branch}")
  println(s"Git Commit    => ${commit}")
  println(s"Scala Version => 2.10.4")
  println()

  val scalaBuildOptions = Seq("-unchecked", "-feature", "-language:reflectiveCalls", "-deprecation",
    "-language:implicitConversions", "-language:postfixOps", "-language:dynamics", "-language:higherKinds",
    "-language:existentials", "-language:experimental.macros", "-Xmax-classfile-name", "140")

  implicit def dependencyFilterer(deps: Seq[ModuleID]) = new Object {
    def excluding(group: String, artifactId: String) =
      deps.map(_.exclude(group, artifactId))
  }

  val sparkVersion = "1.3.0"

  val appDependencies = Seq(ws,

    // GUI
    "org.webjars" %% "webjars-play" % "2.3.0-2",
    "org.webjars" % "angularjs" % "1.2.23",
    "org.webjars" % "bootstrap" % "3.2.0",
    "org.webjars" % "angular-ui-bootstrap" % "0.12.0",
    "org.webjars" % "d3js" % "3.5.3",

    // Spark and Spark Streaming
    "org.apache.spark" %% "spark-core" % sparkVersion,
    "org.apache.spark" %% "spark-streaming" % sparkVersion,
    "org.apache.spark" %% "spark-streaming-kafka" % sparkVersion,

    // Kafka
    "org.apache.kafka" %% "kafka" % "0.8.2.0",

    // Algebird (used here for HyperLogLog)
    "com.twitter" %% "algebird-core" % "0.6.0",

    // MongoDB
    "org.reactivemongo" %% "reactivemongo" % "0.10.5.0.akka23",

    // Joda dates for Scala
    "com.github.nscala-time" %% "nscala-time" % "1.2.0",

    // testing
    "org.scalatestplus" %% "play" % "1.1.0" % "test",
    "com.novocode" % "junit-interface" % "0.11" % "test" //
    )

  val exclusions =
    <dependencies>
			<exclude org="javax.jms" module="jms" />
			<exclude org="com.sun.jdmk" module="jmxtools" />
			<exclude org="com.sun.jmx" module="jmxri" />
			<exclude module="slf4j-jdk14" />
			<exclude module="slf4j-log4j" />
			<exclude module="slf4j-log4j12" />
			<exclude module="slf4j-simple" />
			<exclude module="cglib-nodep" />
    </dependencies>

  val root = Project(appName, file("."))
    .enablePlugins(play.PlayScala)
    .settings(scalacOptions ++= scalaBuildOptions)
    .settings(
      version := appVersion,
      libraryDependencies ++= appDependencies,
      ivyXML := exclusions //  
    )

  println("Done")

}

