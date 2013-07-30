package org.fooblahblah.bivouac

import com.typesafe.config._
import java.util.Date
import model.Model._
import org.apache.commons.codec.binary.Base64
import play.api.libs.json._
import Json._
import scala.util.control.Exception._
import org.slf4j.LoggerFactory
import dispatch._
import com.ning.http.client.Response
import scala.concurrent.ExecutionContext.Implicits.global
import com.ning.http.client.AsyncCompletionHandler
import com.ning.http.client.HttpResponseBodyPart
import com.ning.http.client.AsyncHandler
import scala.util.Try
import scala.util.Failure
import java.util.concurrent.atomic.AtomicBoolean
import com.ning.http.client.HttpResponseStatus
import scala.concurrent.Promise
import scala.concurrent.duration.Duration


trait Client {
  type Payload = Future[Either[String, Array[Byte]]]

  def GET(path: String): Payload

  def POST(path: String, body: Array[Byte]= Array(), contentType: String = "application/json"): Payload

  def PUT(path: String, body: Array[Byte] = Array(), contentType: String = "application/json"): Payload
}


trait Bivouac {
  val OK      = 200
  val CREATED = 201

  protected lazy val logger = LoggerFactory.getLogger(classOf[Bivouac])

  protected val client: Client

  protected val reconnectTimeout: Duration

  protected def campfireConfig: CampfireConfig

  protected def campfireRequest(path: String) = {
    val h = host(s"${campfireConfig.domain}.campfirenow.com").as_!(campfireConfig.token, "X").secure
    h.setUrl(h.url + path)
  }

  protected def streamingRequest(path: String) = {
    val h = host("streaming.campfirenow.com").as_!(campfireConfig.token, "X").secure
    h.setUrl(h.url + path)
  }


  def account: Future[Option[Account]] = client.GET("/account.json") map { response =>
    response match {
      case Right(body) => Some((parse(body) \ "account").as[Account])
      case _           => None
    }
  }


  def rooms: Future[List[Room]] = client.GET("/rooms.json") map { response =>
    response match {
      case Right(body) => parse(body).as[List[Room]]
      case _  => List()
    }
  }


  def room(id: Int): Future[Option[Room]] = client.GET(s"/room/${id}.json") map { response =>
    response match {
      case Right(body) => Some((parse(body) \ "room").as[Room])
      case _           => None
    }
  }


  def presence: Future[List[Room]] = client.GET("/presence.json") map { response =>
    response match {
      case Right(body) => parse(body).as[List[Room]]
      case _           => List()
    }
  }


  def me: Future[Option[User]] = client.GET("/users/me.json") map { response =>
    response match {
      case Right(body) => Some((parse(body) \ "user").as[User])
      case _           => None
    }
  }


  def user(id: Int): Future[Option[User]] = client.GET(s"/users/${id}.json") map { response =>
    response match {
      case Right(body) => Some((parse(body) \ "user").as[User])
      case _           => None
    }
  }


  def join(roomId: Int): Future[Boolean] = client.POST(s"/room/${roomId}/join.json") map { response =>
    response.isRight
  }


  def leave(roomId: Int): Future[Boolean] = client.POST(s"/room/${roomId}/leave.json") map { response =>
    response.isRight
  }


  def updateRoomTopic(roomId: Int, topic: String): Future[Boolean] = {
    val body = Json.obj("room" -> Json.obj("topic" -> JsString(topic))).toString.getBytes()
    client.PUT(s"/room/${roomId}.json", body) map { response =>
      response.isRight
    }
  }


  def speak(roomId: Int, message: String, paste: Boolean = false): Future[Boolean] = {
    val msgType = if(paste) "PasteMessage" else "TextMessage"
    val body = Json.obj("message" -> Json.obj("type" -> msgType, "body" -> message)).toString.getBytes()
    client.POST(s"/room/${roomId}/speak.json", body) map { response =>
      response.isRight
    }
  }


  def recentMessages(roomId: Int): Future[List[Message]] = client.GET(s"/room/${roomId}/recent.json") map { response =>
    response match {
      case Right(body) => parse(body).as[List[Message]]
      case _           => Nil
    }
  }


  def live(roomId: Int, fn: (Message) => Unit): () => Unit = {
    val path   = s"/room/${roomId}/live.json"
    val req    =  streamingRequest(path).GET.toRequest
    val abortP = Promise[Unit]

    def connect: Future[Unit] = {
      logger.info(s"Connecting to room $roomId")

      join(roomId) flatMap { joined =>
        Http(req, new AsyncCompletionHandler[Unit] {
          override def onBodyPartReceived(part: HttpResponseBodyPart) = {
            val line = new String(part.getBodyPartBytes())

            if(!line.trim.isEmpty()) {
              Try {
                new String(part.getBodyPartBytes()).trim split(13.toChar) map(json => fn(parse(json).as[Message]))
              } match {
                case Failure(e) => logger.error("Error parsing message", e)
                case _          =>
              }
            }

            if(!abortP.isCompleted) AsyncHandler.STATE.CONTINUE
            else AsyncHandler.STATE.ABORT
          }

          def onCompleted(res: Response) = {
            tryReconnect(s"Streaming exited for room $roomId")
          }

          override def onThrowable(t: Throwable) {
            tryReconnect(s"Stream for room $roomId exited ${t.getMessage}. Retrying in 15s")
          }
        })
      }
    }

    def tryReconnect(msg: String): Future[Unit] = if(!abortP.isCompleted) {
      logger.info(msg)
      Thread.sleep(reconnectTimeout.toMillis)
      connect
    } else Future.successful()

    connect flatMap { f =>
      tryReconnect(s"Streaming connection for room $roomId exited. Sleeping 15s and reconnecting..")
    }


    () => abortP.success()
  }
}


object Bivouac {

  def apply(): Bivouac = {
    val config           = ConfigFactory.load
    val reconnectTimeout = Duration(failAsValue(classOf[Exception])(config.getString("reconnect-timeout"))("15s"))

    apply(config.getString("domain"), config.getString("token"), reconnectTimeout)
  }


  def apply(domain: String, token: String, timeout: Duration = Duration("15s")): Bivouac = new Bivouac {
    val campfireConfig = CampfireConfig(token, domain)

    val reconnectTimeout = timeout

    val client = new Client {
      def GET(path: String) = Http(campfireRequest(path).GET) map { response =>
        response.getStatusCode match {
          case OK      => Right(response.getResponseBodyAsBytes)
          case _       => Left(response.getStatusText)
        }
      }

      def POST(path: String, body: Array[Byte] = Array(), contentType: String = "application/json") = {
        Http(campfireRequest(path).POST.setBody(body).addHeader("Content-Type", contentType)) map { response =>
          response.getStatusCode match {
            case s if s == OK || s == CREATED => Right(response.getResponseBodyAsBytes)
            case _                            => Left(response.getStatusText)
          }
        }
      }

      def PUT(path: String, body: Array[Byte] = Array(), contentType: String = "application/json") =
        Http(campfireRequest(path).PUT.setBody(body).addHeader("Content-Type", contentType)) map { response =>
          response.getStatusCode match {
            case s if s == OK || s == CREATED => Right(response.getResponseBodyAsBytes)
            case _                            => Left(response.getStatusText)
          }
        }
    }
  }
}
