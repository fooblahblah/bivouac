package org.fooblahblah.bivouac

import akka.dispatch.Future
import blueeyes.core.data.BijectionsChunkJson
import blueeyes.core.data.BijectionsChunkString
import blueeyes.core.data.ByteChunk
import blueeyes.core.http.HttpHeader
import blueeyes.core.http.HttpHeaders
import blueeyes.core.http.HttpHeaders.Authorization
import blueeyes.core.http.HttpMethod
import blueeyes.core.http.HttpMethods._
import blueeyes.core.http.HttpRequest
import blueeyes.core.http.HttpResponse
import blueeyes.core.http.HttpStatusCodes._
import blueeyes.core.http.HttpVersions._
import blueeyes.core.http.MimeTypes._
import blueeyes.core.service.ConfigurableHttpClient
import blueeyes.json.JsonAST._
import blueeyes.json.JsonParser._
import com.weiglewilczek.slf4s.Logger
import java.util.Date
import org.apache.commons.codec.binary.Base64
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter

trait Bivouac extends BijectionsChunkJson with ConfigurableHttpClient {
  private val _logger     = Logger("bivouac")
  private val _dateParser = DateTimeFormat.forPattern("yyyy/MM/dd HH:mm:ss Z")

  protected def config: CampfireConfig

  lazy protected val baseUri      = "https://%s.campfirenow.com".format(config.domain)
  lazy protected val streamingUri = "https://streaming.campfirenow.com"

  def account: Future[Option[Account]] = prepareClient.get[JValue](baseUri + "/account.json") map { response =>
    response.content map(parseAccount(_))
  }

  def rooms: Future[List[Room]] = prepareClient.get[JValue](baseUri + "/rooms.json") map { response =>
    response.content map { parseRooms(_) } getOrElse(List())
  }

  def room(id: Int): Future[Option[Room]] = prepareClient.get[JValue](baseUri + "/room/" + id + ".json") map { response =>
    response.content map { j => parseRoom(j \ "room") }
  }

  def joinRoom(roomId: Int): Future[Boolean] = httpClient.header(authorizationHeader).apply(HttpRequest(POST, baseUri + "/room/" + roomId + "/join.json")) map { response =>
    response.status.code == OK
  }

  def leaveRoom(roomId: Int): Future[Boolean] = httpClient.header(authorizationHeader).apply(HttpRequest(POST, baseUri + "/room/" + roomId + "/leave.json")) map { response =>
    response.status.code == OK
  }

  def recentMessages(roomId: Int): Future[List[Message]] = prepareClient.get[JValue](baseUri + "/room/" + roomId + "/recent.json") map { response =>
    response.content map { parseMessages(_) } getOrElse(List())
  }

  def live(roomId: Int, fn: (Message) => Unit) = httpClient.header(authorizationHeader).apply(HttpRequest(POST, streamingUri + "/room/" + roomId + "/live.json")) map { response =>
    response.content foreach { c =>
      process(c, handleMessageChunk(fn), () => { _logger.debug("done") }, (e: Option[Throwable]) => _logger.error("error in chunk handling " + e))
    }
  } onFailure {
    case e : Throwable =>
      _logger.error("live stream canceled " + e)
      e.printStackTrace()
  }

  def speak(roomId: Int, message: String) = prepareClient.post[JValue](baseUri + "/room/" + roomId + "/speak.json")(Speak(message).toJSON) map { response =>
    response.content.map(j => parseMessage(j \ "message"))
  }

  def presence: Future[Option[List[Room]]] = prepareClient.get[JValue](baseUri + "/presence.json") map { response =>
    response.content map { parseRooms(_) }
  }

  def me: Future[Option[User]] = prepareClient.get[JValue](baseUri + "/users/me.json") map { response =>
    response.content map { parseUser(_) }
  }

  def user(id: Int): Future[Option[User]] = prepareClient.get[JValue](baseUri + "/users/" + id + ".json") map { response =>
    response.content map { parseUser(_) }
  }

  protected def process(chunk: ByteChunk, f: Array[Byte] => Unit, done: () => Unit, error: Option[Throwable] => Unit) {
    f(chunk.data)

    chunk.next match {
      case None => done()
      case Some(e) => {
        e.map(nextChunk => process(nextChunk, f, done, error))
//        e.onFailure {
          // case e: Throwable =>
          //   error(Some(e))
//        }
      }
    }
  }

  def handleMessageChunk(fn: (Message) => Unit)(fragment: Array[Byte]) {
    try {
      if(fragment.length > 1) {
        parse(new String(fragment)) match {
          case j @ JObject(_) => {
            val msg = parseMessage(j)
            fn(msg)
          }
          case _ =>
        }
      }
    } catch {
      case _ : Throwable =>
    }
  }

  protected def parseAccount(jVal: JValue) = Account(
    id        = (jVal \\ "id"         --> classOf[JInt]).value.toInt,
    name      = (jVal \\ "name"       --> classOf[JString]).value,
    subDomain = (jVal \\ "subdomain"  --> classOf[JString]).value,
    plan      = (jVal \\ "plan"       --> classOf[JString]).value,
    ownerId   = (jVal \\ "owner_id"   --> classOf[JInt]).value.toInt,
    timezone  = (jVal \\ "time_zone"  --> classOf[JString]).value,
    createdAt = _dateParser.parseDateTime((jVal \\ "created_at" --> classOf[JString]).value).toDate,
    updatedAt = _dateParser.parseDateTime((jVal \\ "updated_at" --> classOf[JString]).value).toDate
  )

  protected def parseMessages(jVal: JValue) = (jVal \ "messages" --> classOf[JArray]).elements map { parseMessage(_) }

  protected def parseMessage(jObj: JValue) = {
    Message(
      messageType = (jObj \ "type"    --> classOf[JString]).value,
      id          = (jObj \ "id"      --> classOf[JInt]).value.toInt,
      roomId      = (jObj \ "room_id" --> classOf[JInt]).value.toInt,
      userId      = jObj \ "user_id" match {
        case JInt(i) => i.toInt
        case _       => -1
      },
      body        = jObj \ "body" match {
        case JString(s) => s
        case _          => ""
      },
      createdAt   = _dateParser.parseDateTime((jObj \ "created_at" --> classOf[JString]).value).toDate)
  }

  protected def parseRooms(jVal: JValue) = (jVal \ "rooms" --> classOf[JArray]).elements map { parseRoom(_) }

  protected def parseRoom(jObj: JValue) = Room(
    id              = (jObj \ "id"               -->  classOf[JInt]).value.toInt,
    name            = (jObj \ "name"             -->  classOf[JString]).value,
    topic           = (jObj \ "topic"            -->  classOf[JString]).value,
    membershipLimit = (jObj \ "membership_limit" -->  classOf[JInt]).value.toInt,
    locked          = (jObj \ "locked"           -->  classOf[JBool]).value,
    users           = ((jObj \ "users"           -->? classOf[JArray]) map { _.elements map { parseUser(_) } }).getOrElse(Nil),
    createdAt       = _dateParser.parseDateTime((jObj \ "created_at" --> classOf[JString]).value).toDate,
    updatedAt       = _dateParser.parseDateTime((jObj \ "updated_at" --> classOf[JString]).value).toDate)

  protected def parseUser(jObj: JValue) = User(
    id        = (jObj \\ "id"               --> classOf[JInt]).value.toInt,
    name      = (jObj \\ "name"             --> classOf[JString]).value,
    email     = (jObj \\ "email_address"    --> classOf[JString]).value,
    admin     = (jObj \\ "admin"            --> classOf[JBool]).value,
    avatarUrl = (jObj \\ "avatar_url"       --> classOf[JString]).value,
    userType  = (jObj \\ "type"             --> classOf[JString]).value,
    createdAt = _dateParser.parseDateTime((jObj \\ "created_at" --> classOf[JString]).value).toDate)

  protected lazy val authorizationHeader = HttpHeaders.Authorization("Basic " + basicAuthCredentials)

  protected lazy val  prepareClient = httpClient.contentType(application/json).header(authorizationHeader)

  protected lazy val basicAuthCredentials = new String(Base64.encodeBase64((config.token + ":X").getBytes))
}

case class CampfireConfig(token: String, domain: String)

case class Account(id: Int, name: String, subDomain: String, plan: String, ownerId: Int, timezone: String, createdAt: Date, updatedAt: Date)

case class Message(id: Int, roomId: Int, userId: Int, messageType: String, body: String, createdAt: Date)

case class Room(id: Int, name: String, topic: String, membershipLimit: Int, locked: Boolean, createdAt: Date, updatedAt: Date, users: List[User] = Nil)

case class Speak(message: String) {
  def toJSON = JObject(JField("message", JObject(JField("body", JString(message)) :: Nil)) :: Nil)
}

case class User(id: Int, name: String, email: String, admin: Boolean, avatarUrl: String, userType: String, createdAt: Date)
