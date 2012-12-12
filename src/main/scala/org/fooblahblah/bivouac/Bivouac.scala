package org.fooblahblah.bivouac

import akka.actor.ActorSystem
import akka.actor.Props
import com.typesafe.config._
import java.util.Date
import model.Model._
import org.apache.commons.codec.binary.Base64
import play.api.libs.json._
import Json._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import spray.client._
import spray.http._
import spray.http.MediaTypes._
import spray.can.client.HttpClient
import spray.io.IOExtension
import spray.io.SSLContextProvider
import spray.can.client.ClientSettings


trait Bivouac {

//  private val _logger     = Logger("bivouac")

  lazy protected val hostName          = s"${campfireConfig.domain}.campfirenow.com"
  lazy protected val streamingHostName = "streaming.campfirenow.com"

  protected def campfireConfig: CampfireConfig

  protected def pipeline: HttpRequest => Future[HttpResponse]

  def GET(uri: String) = pipeline(HttpRequest(method = HttpMethods.GET, uri = uri))

  def POST(uri: String, body: HttpEntity = EmptyEntity) = pipeline(HttpRequest(method = HttpMethods.POST, uri = uri, entity = body))

  def PUT(uri: String, body: HttpEntity = EmptyEntity) = pipeline(HttpRequest(method = HttpMethods.PUT, uri = uri, entity = body))


  def account: Future[Option[Account]] = GET("/account.json") map { response =>
    response.status match {
      case StatusCodes.OK => Some((parse(response.entity.asString) \ "account").as[Account])
      case _              => None
    }
  }


  def rooms: Future[List[Room]] = GET("/rooms.json") map { response =>
    response.status match {
      case StatusCodes.OK => parse(response.entity.asString).as[List[Room]]
      case _              => List()
    }
  }


  def room(id: Int): Future[Option[Room]] = GET(s"/room/${id}.json") map { response =>
    response.status match {
      case StatusCodes.OK => Some((parse(response.entity.asString) \ "room").as[Room])
      case _              => None
    }
  }


  def presence: Future[List[Room]] = GET("/presence.json") map { response =>
    response.status match {
      case StatusCodes.OK => parse(response.entity.asString).as[List[Room]]
      case _              => List()
    }
  }


  def me: Future[Option[User]] = GET("/users/me.json") map { response =>
    response.status match {
      case StatusCodes.OK => Some((parse(response.entity.asString) \ "user").as[User])
      case _              => None
    }
  }


  def user(id: Int): Future[Option[User]] = GET(s"/users/${id}.json") map { response =>
    response.status match {
      case StatusCodes.OK => Some((parse(response.entity.asString) \ "user").as[User])
      case _              => None
    }
  }


  def joinRoom(roomId: Int): Future[Boolean] = POST(s"/room/${roomId}/join.json") map { response =>
    response.status == StatusCodes.OK
  }


  def leaveRoom(roomId: Int): Future[Boolean] = POST(s"/room/${roomId}/leave.json") map { response =>
    response.status == StatusCodes.OK
  }


  def updateRoomTopic(roomId: Int, topic: String): Future[Boolean] = {
    val body = HttpBody(ContentType(`application/json`), Json.obj("topic" -> JsString(topic)).toString)
    PUT(s"/room/${roomId}.json", body) map { response =>
      response.status == StatusCodes.OK
    }
  }

  def speak(roomId: Int, message: String) = POST(s"/room/${roomId}/speak.json") map { response =>
    response.status == StatusCodes.Created
  }

  def recentMessages(roomId: Int): Future[List[Message]] = GET(s"/room/${roomId}/recent.json") map { response =>
    response.status match {
      case StatusCodes.OK => parse(response.entity.asString).as[List[Message]]
      case _              => Nil
    }
  }

//  def live(roomId: Int, fn: (Message) => Unit) = httpClient.header(authorizationHeader).apply(HttpRequest(POST, streamingUri + "/room/" + roomId + "/live.json")) map { response =>
//    response.content foreach { c =>
//      process(c, handleMessageChunk(fn), () => { _logger.debug("done") }, (e: Option[Throwable]) => _logger.error("error in chunk handling " + e))
//    }
//  } onFailure {
//    case e : Throwable =>
//      _logger.error("live stream canceled " + e)
//      e.printStackTrace()
//  }
//
//  protected def process(chunk: ByteChunk, f: Array[Byte] => Unit, done: () => Unit, error: Option[Throwable] => Unit) {
//    f(chunk.data)
//
//    chunk.next match {
//      case None => done()
//      case Some(e) => {
//        e.map(nextChunk => process(nextChunk, f, done, error))
////        e.onFailure {
//          // case e: Throwable =>
//          //   error(Some(e))
////        }
//      }
//    }
//  }
//
//  def handleMessageChunk(fn: (Message) => Unit)(fragment: Array[Byte]) {
//    try {
//      if(fragment.length > 1) {
//        parse(new String(fragment)) match {
//          case j @ JObject(_) => {
//            val msg = parseMessage(j)
//            fn(msg)
//          }
//          case _ =>
//        }
//      }
//    } catch {
//      case _ : Throwable =>
//    }
//  }

  protected lazy val authorizationHeader = BasicHttpCredentials(campfireConfig.token, "X")
}


object Bivouac {
  import HttpConduit._
  import SSLContextProvider._

  def apply() = new Bivouac {
    val config         = ConfigFactory.load
    val campfireConfig = CampfireConfig(config.getString("token"), config.getString("domain"))

    implicit val system = ActorSystem()
    val ioBridge = IOExtension(system).ioBridge
    val httpClient = system.actorOf(Props(new HttpClient(ioBridge)))

    val conduit = system.actorOf(
      props = Props(new HttpConduit(httpClient, hostName, 443, true)),
      name = "http-conduit"
    )

    val pipeline =
      addCredentials(authorizationHeader) ~>
      sendReceive(conduit)
  }
}
