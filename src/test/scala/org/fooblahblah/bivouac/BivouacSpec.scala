package org.fooblahblah.bivouac

import akka.dispatch.Future
import akka.dispatch.Futures
import blueeyes.Environment
import blueeyes.core.data.ByteChunk
import blueeyes.core.data.BijectionsChunkFutureJson
import blueeyes.core.http.HttpRequest
import blueeyes.core.http.HttpResponse
import blueeyes.core.http.HttpStatusCodes._
import blueeyes.core.http.MimeTypes._
import blueeyes.core.service.HttpClientByteChunk
import blueeyes.core.service.HttpRequestHandlerCombinators
import blueeyes.json.JsonAST._
import blueeyes.json.JsonParser._
import com.weiglewilczek.slf4s.Logger
import org.specs2.matcher.Matchers._
import org.specs2.mutable.Specification
import org.specs2.time.TimeConversions._


class BivouacSpec extends Specification with Bivouac with HttpRequestHandlerCombinators {

  val logger = Logger("bivouacSpec")

  val config = CampfireConfig("123456", "foo")
  val roomId = 33333

  sys.props.put(Environment.MockSwitch, "true")

  "Bivouac" should {
    "Form Authorization header with token" in {
      val h = authorizationHeader
      logger.debug(h.toString)
      h.toString must startWith("Authorization:")
    }

    "Support account" in {
      var res: Option[Account] = None
      account.map(res = _)
      res must eventually(beSome(parseAccount(accountArtifact)))
      logger.debug(res.toString)
    }

    "Support rooms" in {
      var res: List[Room] = Nil
      rooms.map(res = _)
      res must eventually(be_==(parseRooms(roomsArtifact)))
      logger.debug(res.toString)
    }

    "Support presence" in {
      var res: Option[List[Room]] = None
      presence.map(res = _)
      res must eventually(beSome(parseRooms(roomsArtifact)))
      logger.debug(res.toString)
    }

    "Support users/me" in {
      var res: Option[User] = None
      me.map(res = _)
      res must eventually(beSome(parseUser(meArtifact)))
      logger.debug(res.toString)
    }

    "Support user by id" in {
      var res: Option[User] = None
      user(5555).map(res = _)
      res must eventually(beSome(parseUser(userArtifact)))
      logger.debug(res.toString)
    }

    "Support room" in {
      var res: Option[Room] = None
      room(roomId).map(res = _)
      res must eventually(beSome(parseRoom((roomsArtifact \ "rooms" --> classOf[JArray]).elements.head)))
      logger.debug(res.toString)
    }

    "Support recent messages" in {
      var res: List[Message] = Nil
      recentMessages(roomId).map(res = _)
      res must eventually(be_==(parseMessages(messagesArtifact)))
      logger.debug(res.toString)
    }

    "Support joining a room" in {
      var res: Boolean = false
      joinRoom(roomId).map(res = _)
      res must eventually(beTrue)
      logger.debug(res.toString)
    }

    "Support leaving a room" in {
      var res: Boolean = false
      leaveRoom(roomId).map(res = _)
      res must eventually(beTrue)
      logger.debug(res.toString)
    }

    "Support posting a message" in {
      var res: Option[Message] = None
      speak(roomId, "Testing Bivouac API (please ignore)").map(res = _)
      res must eventually(beSome[Message])
      logger.debug(res.toString)
    }
  }

  import blueeyes.core.data.BijectionsChunkFutureJson._

  override val mockServer =
    path("/account.json") {
      produce(application/json) {
        get { request: HttpRequest[ByteChunk] =>
          Future(HttpResponse[JValue](content = Some(accountArtifact)))
        }
      }
    } ~
    path("/rooms.json") {
      produce(application/json) {
        get { request: HttpRequest[ByteChunk] =>
          Future(HttpResponse[JValue](content = Some(roomsArtifact)))
        }
      }
    } ~
    path("/room/33333.json") {
      produce(application/json) {
        get { request: HttpRequest[ByteChunk] =>
          val r = JObject(JField("room", (roomsArtifact \ "rooms" --> classOf[JArray]).elements.head) :: Nil)
          Future(HttpResponse[JValue](content = Some(r)))
        }
      }
    } ~
    path("/room/33333/recent.json") {
      produce(application/json) {
        get { request: HttpRequest[ByteChunk] =>
          Future(HttpResponse[JValue](content = Some(messagesArtifact)))
        }
      }
    } ~
    path("/room/33333/join.json") {
      produce(application/json) {
        post { request: HttpRequest[ByteChunk] =>
          Future(HttpResponse[JValue]())
        }
      }
    } ~
    path("/room/33333/leave.json") {
      produce(application/json) {
        post { request: HttpRequest[ByteChunk] =>
          Future(HttpResponse[JValue]())
        }
      }
    } ~
    path("/room/33333/speak.json") {
      jvalue {
        post { request: HttpRequest[Future[JValue]] =>
          request.content match {
            case Some(future) =>
            future.map { c =>
              val msg = JObject(JField("message", JObject(
                JField("id", JInt(1234)) ::
                JField("user_id", JInt(1234)) ::
                JField("room_id", JInt(roomId)) ::
                JField("type", JString("TextMessage")) ::
                JField("created_at", JString("2011/09/14 21:20:11 +0000")) ::
                JField("body", JString((c \\ "body" --> classOf[JString]).value)) :: Nil)) :: Nil)
              HttpResponse[JValue](status = Created, content = Some(msg))
            }
            case None =>
            Future(HttpResponse[JValue](content = None))
          }
        }
      }
    } ~
    path("/presence.json") {
      produce(application/json) {
        get { request: HttpRequest[ByteChunk] =>
          Future(HttpResponse[JValue](content = Some(roomsArtifact)))
        }
      }
    } ~
    path("/users/me.json") {
      produce(application/json) {
        get { request: HttpRequest[ByteChunk] =>
          Future(HttpResponse[JValue](content = Some(meArtifact)))
        }
      }
    } ~
    path("/users/5555.json") {
      produce(application/json) {
        get { request: HttpRequest[ByteChunk] =>
          Future(HttpResponse[JValue](content = Some(userArtifact)))
        }
      }
    }


  val accountArtifact = parse("""
    {
      "account": {
        "updated_at": "2011/09/07 11:51:16 +0000",
        "owner_id": 1234,
        "plan": "premium",
        "created_at": "2011/04/14 20:55:05 +0000",
        "time_zone": "America/New_York",
        "name": "Fooblahblah",
        "id": 1111,
        "storage": 11111,
        "subdomain": "fooblahblah"
      }
    }
    """)

  val roomsArtifact = parse("""
    {
      "rooms": [
        {
          "name": "Foo",
          "created_at": "2011/04/14 20:55:05 +0000",
          "updated_at": "2011/04/21 23:01:15 +0000",
          "topic": "Blah",
          "id": 22222,
          "membership_limit": 60,
          "locked": false
        },
        {
          "name": "Blah",
          "created_at": "2011/05/09 17:52:05 +0000",
          "updated_at": "2011/05/09 17:52:05 +0000",
          "topic": "Foo Blah",
          "id": 33333,
          "membership_limit": 60,
          "locked": false
        }
      ]
    }
  """)

  val meArtifact = parse("""
    {
      "user": {
        "type": "Member",
        "avatar_url": "http://asset0.37img.com/global/missing/avatar.png?r=3",
        "created_at": "2011/04/27 15:20:10 +0000",
        "admin": true,
        "id": 4444,
        "name": "Jeff Simpson",
        "email_address": "fooblahblah@fooblahblah.org",
        "api_auth_token": "123456676"
      }
    }
    """)

  val userArtifact = parse("""
    {
      "user": {
        "type": "Member",
        "avatar_url": "http://asset0.37img.com/global/missing/avatar.png?r=3",
        "created_at": "2011/04/27 15:20:10 +0000",
        "admin": true,
        "id": 5555,
        "name": "John Doe",
        "email_address": "john.doe@fooblahblah.org",
        "api_auth_token": "123456456"
      }
    }
    """)

  val messagesArtifact = parse("""
    {
      "messages": [
        {
          "type": "TimestampMessage",
          "room_id": 33333,
          "created_at": "2011/09/13 15:40:00 +0000",
          "id": 409398730,
          "body": null,
          "user_id": null
        },
        {
          "type": "EnterMessage",
          "room_id": 33333,
          "created_at": "2011/09/13 15:44:33 +0000",
          "id": 409398731,
          "body": null,
          "user_id": 12345
        },
        {
          "type": "TextMessage",
          "room_id": 33333,
          "created_at": "2011/09/14 16:33:21 +0000",
          "id": 410124653,
          "body": "anyone still having problems getting into the room?",
          "user_id": 45347
        },
        {
          "type": "TextMessage",
          "room_id": 33333,
          "created_at": "2011/09/14 17:04:03 +0000",
          "id": 410149236,
          "body": "i am guessing some people are just not used to signing in and using this tool",
          "user_id": 23423423
        },
        {
          "type": "KickMessage",
          "room_id": 33333,
          "created_at": "2011/09/14 17:40:19 +0000",
          "id": 410176731,
          "body": null,
          "user_id": 935596
        }
      ]
    }
    """)
}
