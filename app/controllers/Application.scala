package controllers

import play.api._
import play.api.mvc._
import scala.collection.JavaConversions.asScalaBuffer
import scala.compat.Platform
import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }
import akka.actor.{ PoisonPill, Props, actorRef2Scala }
import play.api.http.MimeTypes
import play.api.libs.EventSource
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee.{ Concurrent, Enumeratee, Enumerator, Iteratee }
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, BodyParsers, Controller, MultipartFormData, ResponseHeader, Result }
import play.libs.Akka
import java.util.UUID
import me.lightspeed7.dsug.ui.Ui.Listener

object Application extends Controller {

  def index: Action[AnyContent] = Action {
    val uuid = UUID.randomUUID().toString()
    Ok(views.html.index(uuid))
  }

  def sse(uuid: String) = Action.async { req =>

    Future {
      println(req.remoteAddress + " - SSE connected")
      val (out, channel) = Concurrent.broadcast[JsValue]

      // create unique actor for each uuid
      println(s"Creating Listener - ${uuid}")
      val listener = Akka.system.actorOf(Props(classOf[Listener], uuid, channel))

      def connDeathWatch(addr: String): Enumeratee[JsValue, JsValue] =
        Enumeratee.onIterateeDone { () =>
          {
            println(addr + " - SSE disconnected")
            listener ! PoisonPill
          }
        }

      val result =
        Ok.feed(out
          &> Concurrent.buffer(300)
          &> connDeathWatch(req.remoteAddress)
          &> EventSource()).as("text/event-stream")

      //      Akka.system.actorOf(Props(classOf[Loader], "Loader" + uuid)) ! Load(listener)
      result
    }

  }
}
