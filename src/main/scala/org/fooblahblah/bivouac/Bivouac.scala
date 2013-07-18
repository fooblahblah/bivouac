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

  implicit def system: ActorSystem

  protected lazy val logger = Logging(system, "Bivouac")

  protected lazy val authorizationCreds = BasicHttpCredentials(campfireConfig.token, "X")
  protected lazy val hostName           = s"${campfireConfig.domain}.campfirenow.com"
  protected lazy val streamingHostName  = "streaming.campfirenow.com"

  protected def campfireConfig: CampfireConfig

  protected def client: HttpRequest => Future[HttpResponse]

  protected def streamingClient: ActorRef


  def GET(uri: String) = client(HttpRequest(method = HttpMethods.GET, uri = uri))

  def POST(uri: String, body: HttpEntity = EmptyEntity) = client(HttpRequest(method = HttpMethods.POST, uri = uri, entity = body))

  def PUT(uri: String, body: HttpEntity = EmptyEntity) = client(HttpRequest(method = HttpMethods.PUT, uri = uri, entity = body))


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


  def join(roomId: Int): Future[Boolean] = POST(s"/room/${roomId}/join.json") map { response =>
    response.status == StatusCodes.OK
  }


  def leave(roomId: Int): Future[Boolean] = POST(s"/room/${roomId}/leave.json") map { response =>
    response.status == StatusCodes.OK
  }


  def updateRoomTopic(roomId: Int, topic: String): Future[Boolean] = {
    val body = HttpBody(ContentType(`application/json`), Json.obj("room" -> Json.obj("topic" -> JsString(topic))).toString)
    PUT(s"/room/${roomId}.json", body) map { response =>
      response.status == StatusCodes.OK
    }
  }


  def speak(roomId: Int, message: String, paste: Boolean = false): Future[Boolean] = {
    val msgType = if(paste) "PasteMessage" else "TextMessage"
    val body = HttpBody(ContentType(`application/json`), Json.obj("message" -> Json.obj("type" -> msgType, "body" -> message)).toString)
    POST(s"/room/${roomId}/speak.json", body) map { response =>
      response.status == StatusCodes.Created
    }
  }

  def recentMessages(roomId: Int): Future[List[Message]] = GET(s"/room/${roomId}/recent.json") map { response =>
    response.status match {
      case StatusCodes.OK => parse(response.entity.asString).as[List[Message]]
      case _              => Nil
    }
  }

  def live(roomId: Int, fn: (Message) => Unit): ActorRef = {

    class Streamer extends Actor {
      val uri = s"/room/${roomId}/live.json"

      var retrying: Boolean = false

      def reconnect() {
        retrying = true
        logger.info("Retrying connection in 3s ...")
        system.scheduler.scheduleOnce(3 seconds, self, Connect)
      }

      def receive = {
        case Connect =>
          logger.info(s"Attempting to connect: $streamingHostName$uri")
          streamingClient ! Connect(streamingHostName, 443, HttpClient.SslEnabled)
          sender ! true

        case Status.Failure(reason) =>
          logger.info(s"Failed to connect: ${reason}")
          reconnect()

        case Connected(connection) =>
          logger.info(s"Connected to stream: $streamingHostName$uri")
          retrying = false
          val request = HttpRequest(method = HttpMethods.GET, uri = uri, headers = Authorization(authorizationCreds) :: Nil)
          connection.handler ! request

        case m: MessageChunk if(!m.bodyAsString.trim.isEmpty) =>
          m.bodyAsString.trim split(13.toChar) map(json => fn(parse(json).as[Message]))

        case MessageChunk(_, _) =>

        case Closed(reason) =>
          logger.info(s"Connection closed: ${reason}")
          if(!retrying) reconnect()

        case m =>
          logger.info(s"${m}")
      }
    }

    val streamer = system.actorOf(Props(new Streamer))
    streamer ! Connect

    streamer
  }
}


object Bivouac {
  import HttpConduit._
  import SSLContextProvider._
  import scala.util.control.Exception._

  def apply()(implicit system: ActorSystem): Bivouac = {
    val config           = ConfigFactory.load
    val reconnectTimeout = failAsValue(classOf[Exception])(config.getInt("reconnect-timeout"))(5)

    apply(config.getString("domain"), config.getString("token"), reconnectTimeout)
  }

  def apply(domain: String, token: String, reconnectTimeout: Int = 5)(implicit system_ : ActorSystem): Bivouac = new Bivouac {

    val system = system_

    val campfireConfig = CampfireConfig(token, domain)

    val ioBridge = IOExtension(system).ioBridge
    val httpClient = system.actorOf(Props(new HttpClient(ioBridge)))

    val conduit = system.actorOf(Props(new HttpConduit(httpClient, hostName, 443, true)))

    val client =
      addCredentials(authorizationCreds) ~>
      sendReceive(conduit)

    val clientConfig = """
      spray.can.client.ssl-encryption = on
      spray.can.client.response-chunk-aggregation-limit = 0
      spray.can.client.idle-timeout = 10s
      spray.can.client.request-timeout = 0s
      """
    val settings = ClientSettings(ConfigFactory.parseString(clientConfig))
    val streamingClient = system.actorOf(Props(new HttpClient(ioBridge, settings)))
  }
}
