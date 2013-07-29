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

trait Bivouac {

  protected lazy val logger = LoggerFactory.getLogger(classOf[Bivouac])

  protected lazy val streamingHostName  = "streaming.campfirenow.com"

  protected def campfireUri(path: String) = {
    val h = host(s"${campfireConfig.domain}.campfirenow.com").secure.as(campfireConfig.token, "X")
    h.setUrl(h.url + path)
  }

  protected def campfireConfig: CampfireConfig

//  protected def client: Req => Future[Response]

  val OK      = 200
  val CREATED = 201

  def GET(path: String) = Http(campfireUri(path).GET)

  def POST(path: String, body: Array[Byte]= Array(), contentType: String = "application/json") =  Http(campfireUri(path).POST.setBody(body).addHeader("Content-Type", contentType))

  def PUT(path: String, body: Array[Byte] = Array(), contentType: String = "application/json") = Http(campfireUri(path).PUT.setBody(body).addHeader("Content-Type", contentType))


  def account: Future[Option[Account]] = GET("/account.json") map { response =>
    response.getStatusCode match {
      case OK  => Some((parse(response.getResponseBody) \ "account").as[Account])
      case _   => None
    }
  }


  def rooms: Future[List[Room]] = GET("/rooms.json") map { response =>
    response.getStatusCode match {
      case OK => parse(response.getResponseBody).as[List[Room]]
      case _  => List()
    }
  }


  def room(id: Int): Future[Option[Room]] = GET(s"/room/${id}.json") map { response =>
    response.getStatusCode match {
      case OK => Some((parse(response.getResponseBody) \ "room").as[Room])
      case _  => None
    }
  }


  def presence: Future[List[Room]] = GET("/presence.json") map { response =>
    response.getStatusCode match {
      case OK => parse(response.getResponseBody).as[List[Room]]
      case _  => List()
    }
  }


  def me: Future[Option[User]] = GET("/users/me.json") map { response =>
    response.getStatusCode match {
      case OK => Some((parse(response.getResponseBody()) \ "user").as[User])
      case _  => None
    }
  }


  def user(id: Int): Future[Option[User]] = GET(s"/users/${id}.json") map { response =>
    response.getStatusCode match {
      case OK => Some((parse(response.getResponseBody) \ "user").as[User])
      case _  => None
    }
  }


  def join(roomId: Int): Future[Boolean] = POST(s"/room/${roomId}/join.json") map { response =>
    response.getStatusCode == OK
  }


  def leave(roomId: Int): Future[Boolean] = POST(s"/room/${roomId}/leave.json") map { response =>
    response.getStatusCode == OK
  }


  def updateRoomTopic(roomId: Int, topic: String): Future[Boolean] = {
    val body = Json.obj("room" -> Json.obj("topic" -> JsString(topic))).toString.getBytes()
    PUT(s"/room/${roomId}.json", body) map { response =>
      response.getStatusCode == OK
    }
  }


  def speak(roomId: Int, message: String, paste: Boolean = false): Future[Boolean] = {
    val msgType = if(paste) "PasteMessage" else "TextMessage"
    val body = Json.obj("message" -> Json.obj("type" -> msgType, "body" -> message)).toString.getBytes()
    POST(s"/room/${roomId}/speak.json", body) map { response =>
      response.getStatusCode == CREATED
    }
  }


  def recentMessages(roomId: Int): Future[List[Message]] = GET(s"/room/${roomId}/recent.json") map { response =>
    response.getStatusCode match {
      case OK => parse(response.getResponseBody()).as[List[Message]]
      case _  => Nil
    }
  }

  def live(roomId: Int, fn: (Message) => Unit) {
      val path = s"/room/${roomId}/live.json"

      GET(path) map { r =>
        r.getResponseBodyAsStream()
      }
  }
}


object Bivouac {

  def apply(): Bivouac = {
    val config = ConfigFactory.load

    apply(config.getString("domain"), config.getString("token"))
  }

  def apply(domain: String, token: String): Bivouac = new Bivouac {

    val campfireConfig = CampfireConfig(token, domain)


  }
}
