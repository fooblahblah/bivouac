package org.fooblahblah.bivouac

import java.util.concurrent.TimeUnit
import org.junit.runner._
import org.specs2.matcher.Matchers._
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.time.TimeConversions._
import play.api.libs.json._
import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import spray.http._

@RunWith(classOf[JUnitRunner])
class BivouacSpec extends Specification with Bivouac {

  sequential

  val campfireConfig = CampfireConfig("123456", "foo")
  val roomId         = 22222
  val userId         = 5555

  "Bivouac" should {
    "Form Authorization header with token" in {
      val h = authorizationHeader
      h.toString must startWith("Basic ")
    }

    "Support account" in {
      val result = Await.result(account, Duration(1, TimeUnit.SECONDS))
      result must beAnInstanceOf[Option[Account]]
      result.map(_.id) === Some(1111)
    }

    "Support rooms" in {
      val result = Await.result(rooms, Duration(1, TimeUnit.SECONDS))
      result must beAnInstanceOf[List[Room]]
      result.map(_.id) === List(22222, 33333)
    }

    "Support get room by id" in {
      val result = Await.result(room(roomId), Duration(1, TimeUnit.SECONDS))
      result must beAnInstanceOf[Option[Room]]
      result.map(_.id) === Some(22222)
    }

    "Support presence" in {
      val result = Await.result(presence, Duration(1, TimeUnit.SECONDS))
      result must beAnInstanceOf[List[Room]]
      result.map(_.id) === List(22222, 33333)
    }

    "Support users/me" in {
      val result = Await.result(me, Duration(1, TimeUnit.SECONDS))
      result must beAnInstanceOf[Option[User]]
      result.map(_.name) === Some("Jeff Simpson")
    }

    "Support user by id" in {
      val result = Await.result(user(5555), Duration(1, TimeUnit.SECONDS))
      result must beAnInstanceOf[Option[User]]
      result.map(_.name) === Some("John Doe")
    }

    "Support joining a room" in {
      val result = Await.result(joinRoom(roomId), Duration(1, TimeUnit.SECONDS))
      result === true
    }

    "Support leaving a room" in {
      val result = Await.result(leaveRoom(roomId), Duration(1, TimeUnit.SECONDS))
      result === true
    }

    "Support updating a room topic" in {
      val result = Await.result(updateRoomTopic(roomId, "blah"), Duration(1, TimeUnit.SECONDS))
      result === true
    }

//
//    "Support recent messages" in {
//      var res: List[Message] = Nil
//      recentMessages(roomId).map(res = _)
//      res must eventually(be_==(parseMessages(messagesArtifact)))
//      logger.debug(res.toString)
//    }
//
//    "Support posting a message" in {
//      var res: Option[Message] = None
//      speak(roomId, "Testing Bivouac API (please ignore)").map(res = _)
//      res must eventually(beSome[Message])
//      logger.debug(res.toString)
//    }
  }


  val pipeline = { request: HttpRequest =>
    import CampfireJsonProtocol._
    import HttpMethods._

    val firstRoom = Json.obj("room" -> (Json.parse(roomsArtifact) \ "rooms")(0))

    (request.method, request.uri) match {
      case (GET,  "/account.json")          => Future.successful(HttpResponse(status = StatusCodes.OK, entity = HttpBody(accountArtifact)))
      case (GET,  "/rooms.json")            => Future.successful(HttpResponse(status = StatusCodes.OK, entity = HttpBody(roomsArtifact)))
      case (GET,  "/room/22222.json")       => Future.successful(HttpResponse(status = StatusCodes.OK, entity = HttpBody(firstRoom.toString)))
      case (POST, "/room/22222/join.json")  => Future.successful(HttpResponse(status = StatusCodes.OK))
      case (POST, "/room/22222/leave.json") => Future.successful(HttpResponse(status = StatusCodes.OK))
      case (PUT,  "/room/22222.json")       => Future.successful(HttpResponse(status = StatusCodes.OK))
      case (GET,  "/presence.json")         => Future.successful(HttpResponse(status = StatusCodes.OK, entity = HttpBody(roomsArtifact)))
      case (GET,  "/users/me.json")         => Future.successful(HttpResponse(status = StatusCodes.OK, entity = HttpBody(meArtifact)))
      case (GET,  "/users/5555.json")       => Future.successful(HttpResponse(status = StatusCodes.OK, entity = HttpBody(userArtifact)))
      case _                                => Future.successful(HttpResponse(status = StatusCodes.NotFound))
    }

  }

  val accountArtifact = """
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
    """

  val roomsArtifact = """
    {
      "rooms": [
        {
          "name": "Foo",
          "created_at": "2011/04/14 20:55:05 +0000",
          "updated_at": "2011/04/21 23:01:15 +0000",
          "topic": "Blah",
          "id": 22222,
          "membership_limit": 60,
          "locked": false,
          "users" : []
        },
        {
          "name": "Blah",
          "created_at": "2011/05/09 17:52:05 +0000",
          "updated_at": "2011/05/09 17:52:05 +0000",
          "topic": "Foo Blah",
          "id": 33333,
          "membership_limit": 60,
          "locked": false,
          "users" : []
        }
      ]
    }
  """

  val meArtifact = """
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
    """

  val userArtifact = """
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
    """

  val messagesArtifact = """
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
    """
}

//  override val mockServer =
//    path("/account.json") {
//      produce(application/json) {
//        get { request: HttpRequest[ByteChunk] =>
//          Future(HttpResponse[JValue](content = Some(accountArtifact)))
//        }
//      }
//    } ~
//    path("/rooms.json") {
//      produce(application/json) {
//        get { request: HttpRequest[ByteChunk] =>
//          Future(HttpResponse[JValue](content = Some(roomsArtifact)))
//        }
//      }
//    } ~
//    path("/room/33333.json") {
//      produce(application/json) {
//        get { request: HttpRequest[ByteChunk] =>
//          val r = JObject(JField("room", (roomsArtifact \ "rooms" --> classOf[JArray]).elements.head) :: Nil)
//          Future(HttpResponse[JValue](content = Some(r)))
//        }
//      }
//    } ~
//    path("/room/33333/recent.json") {
//      produce(application/json) {
//        get { request: HttpRequest[ByteChunk] =>
//          Future(HttpResponse[JValue](content = Some(messagesArtifact)))
//        }
//      }
//    } ~
//    path("/room/33333/join.json") {
//      produce(application/json) {
//        post { request: HttpRequest[ByteChunk] =>
//          Future(HttpResponse[JValue]())
//        }
//      }
//    } ~
//    path("/room/33333/leave.json") {
//      produce(application/json) {
//        post { request: HttpRequest[ByteChunk] =>
//          Future(HttpResponse[JValue]())
//        }
//      }
//    } ~
//    path("/room/33333/speak.json") {
//      jvalue {
//        post { request: HttpRequest[Future[JValue]] =>
//          request.content match {
//            case Some(future) =>
//            future.map { c =>
//              val msg = JObject(JField("message", JObject(
//                JField("id", JInt(1234)) ::
//                JField("user_id", JInt(1234)) ::
//                JField("room_id", JInt(roomId)) ::
//                JField("type", JString("TextMessage")) ::
//                JField("created_at", JString("2011/09/14 21:20:11 +0000")) ::
//                JField("body", JString((c \\ "body" --> classOf[JString]).value)) :: Nil)) :: Nil)
//              HttpResponse[JValue](status = Created, content = Some(msg))
//            }
//            case None =>
//            Future(HttpResponse[JValue](content = None))
//          }
//        }
//      }
//    } ~
//    path("/presence.json") {
//      produce(application/json) {
//        get { request: HttpRequest[ByteChunk] =>
//          Future(HttpResponse[JValue](content = Some(roomsArtifact)))
//        }
//      }
//    } ~
//    path("/users/me.json") {
//      produce(application/json) {
//        get { request: HttpRequest[ByteChunk] =>
//          Future(HttpResponse[JValue](content = Some(meArtifact)))
//        }
//      }
//    } ~
//    path("/users/5555.json") {
//      produce(application/json) {
//        get { request: HttpRequest[ByteChunk] =>
//          Future(HttpResponse[JValue](content = Some(userArtifact)))
//        }
//      }
//    }
