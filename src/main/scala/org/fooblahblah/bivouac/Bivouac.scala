package org.fooblahblah.bivouac

import akka.actor._
import akka.actor.ActorDSL._
import akka.event.Logging
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config._
import java.util.Date
import model.Model._
import org.apache.commons.codec.binary.Base64
import play.api.libs.json._
import Json._
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import spray.client._
import spray.http._
import spray.http.MediaTypes._
import spray.can.client.HttpClient
import spray.can.client.HttpClient._
import spray.io.IOExtension
import spray.io.SSLContextProvider
import spray.can.client.ClientSettings
import spray.http.HttpHeaders.Authorization


trait Bivouac {

  implicit lazy val system = ActorSystem()

  protected lazy val logger = Logging(system, "Bivouac")

  protected lazy val authorizationCreds = BasicHttpCredentials(campfireConfig.token, "X")
  protected lazy val hostName           = s"${campfireConfig.domain}.campfirenow.com"
  protected lazy val streamingHostName  = "streaming.campfirenow.com"

  protected def campfireConfig: CampfireConfig

  protected def pipeline: HttpRequest => Future[HttpResponse]

  protected def streamingClient: ActorRef


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


  def live(roomId: Int, fn: (Message) => Unit) = {
    implicit val futureTimeout = Timeout(5 seconds)

    val streamer = actor(new Act {
      become {
        case Connect =>
          streamingClient ! Connect(streamingHostName, 443, HttpClient.SslEnabled)

        case Connected(connection) =>
          logger.info("Connected to stream")
          val request = HttpRequest(method = HttpMethods.GET, uri = s"/room/${roomId}/live.json", headers = Authorization(authorizationCreds) :: Nil)
          connection.handler ! request

        case m: MessageChunk if(!m.bodyAsString.trim.isEmpty) =>
          fn(parse(m.bodyAsString.trim).as[Message])

        case MessageChunk(_, _) =>

        case m =>
          logger.info(s"${m}")
      }
    })

    streamer ! Connect

    Future(true)
  }
}


object Bivouac {
  import HttpConduit._
  import SSLContextProvider._

  def apply() = new Bivouac {
    val config         = ConfigFactory.load
    val campfireConfig = CampfireConfig(config.getString("token"), config.getString("domain"))

    val ioBridge = IOExtension(system).ioBridge
    val httpClient = system.actorOf(Props(new HttpClient(ioBridge)))

    val conduit = system.actorOf(
      props = Props(new HttpConduit(httpClient, hostName, 443, true)),
      name = "http-conduit"
    )

    val pipeline =
      addCredentials(authorizationCreds) ~>
      sendReceive(conduit)

    val clientConfig = """
      spray.can.client.ssl-encryption = on
      spray.can.client.response-chunk-aggregation-limit = 0
      spray.can.client.idle-timeout = 0
      """
    val settings = ClientSettings(ConfigFactory.parseString(clientConfig))
    val streamingClient = system.actorOf(props = Props(
        new HttpClient(ioBridge, settings)),
        name = "streaming-client")
  }
}
