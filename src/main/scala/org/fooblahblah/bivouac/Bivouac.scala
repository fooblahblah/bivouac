package org.fooblahblah.bivouac

import akka.actor.ActorSystem
import akka.actor.Props
import com.typesafe.config._
import java.util.Date
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
  import CampfireJsonProtocol._
  import HttpConduit._

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
  } recover {
    case e: Throwable =>
      e.printStackTrace()
      List()
  }


  def room(id: Int): Future[Option[Room]] = GET(s"/room/${id}.json") map { response =>
    println(response.entity.asString)
    response.status match {
      case StatusCodes.OK => Some((parse(response.entity.asString) \ "room").as[Room])
      case _              => None
    }
  } recover {
    case e: Throwable =>
      e.printStackTrace()
      None
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

//  def recentMessages(roomId: Int): Future[List[Message]] = prepareClient.get[JValue](baseUri + "/room/" + roomId + "/recent.json") map { response =>
//    response.content map { parseMessages(_) } getOrElse(List())
//  }
//
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
//  def speak(roomId: Int, message: String) = prepareClient.post[JValue](baseUri + "/room/" + roomId + "/speak.json")(Speak(message).toJSON) map { response =>
//    response.content.map(j => parseMessage(j \ "message"))
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


case class CampfireConfig(token: String, domain: String)

case class Account(id: Int, name: String, subDomain: String, plan: String, ownerId: Int, timezone: String, createdAt: Date, updatedAt: Date)

case class Message(id: Int, roomId: Int, userId: Int, messageType: String, body: String, createdAt: Date)

case class Room(id: Int, name: String, topic: String, membershipLimit: Int, locked: Boolean, createdAt: Date, updatedAt: Date, users: Option[List[User]] = None)

case class Speak(message: String) {
  def toJSON = Json.obj("message" -> Json.obj("body" -> JsString(message)))
}

case class User(id: Int, name: String, email: String, admin: Boolean, avatarUrl: String, userType: String, createdAt: Date)


object CampfireJsonProtocol {
  import play.api.libs.json.Reads._
  import play.api.libs.json.util._
  import play.api.libs.functional.syntax._

  implicit val customDateReads = dateReads("yyyy/MM/dd HH:mm:ss Z")

  implicit val accountReads: Reads[Account] = (
    (__ \ "id").read[Int] ~
    (__ \ "name").read[String] ~
    (__ \ "subdomain").read[String] ~
    (__ \ "plan").read[String] ~
    (__ \ "owner_id").read[Int] ~
    (__ \ "time_zone").read[String] ~
    (__ \ "created_at").read[Date](customDateReads) ~
    (__ \ "updated_at").read[Date](customDateReads))(Account)


  implicit val userReads: Reads[User] = (
    (__ \ "id").read[Int] ~
    (__ \ "name").read[String] ~
    (__ \ "email_address").read[String] ~
    (__ \ "admin").read[Boolean] ~
    (__ \ "avatar_url").read[String] ~
    (__ \ "type").read[String] ~
    (__ \ "created_at").read[Date](customDateReads))(User)

  implicit val listUserReads: Reads[List[User]] = ((__ \ "users").read(list[User]))


  implicit val roomReads: Reads[Room] = (
    (__ \ "id").read[Int] ~
    (__ \ "name").read[String] ~
    (__ \ "topic").read[String] ~
    (__ \ "membership_limit").read[Int] ~
    (__ \ "locked").read[Boolean] ~
    (__ \ "created_at").read[Date](customDateReads) ~
    (__ \ "updated_at").read[Date](customDateReads) ~
    (__ \ "users").readOpt(list[User]))(Room)

  implicit val listRoomReads: Reads[List[Room]] = ((__ \ "rooms").read(list[Room]))

}
