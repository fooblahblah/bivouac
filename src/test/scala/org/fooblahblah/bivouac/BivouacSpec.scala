package org.fooblahblah.bivouac

import java.util.concurrent.TimeUnit
import model.Model._
import org.junit.runner._
import org.specs2.matcher.Matchers._
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.time.TimeConversions._
import play.api.libs.json._
import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import dispatch.Req

@RunWith(classOf[JUnitRunner])
class BivouacSpec extends Specification with Bivouac {

  sequential

  val campfireConfig = CampfireConfig("123456", "foo")
  val roomId         = 22222
  val userId         = 5555

  val reconnectTimeout = Duration("5s")

  "Bivouac" should {
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
      val result = Await.result(join(roomId), Duration(1, TimeUnit.SECONDS))
      result === true
    }

    "Support leaving a room" in {
      val result = Await.result(leave(roomId), Duration(1, TimeUnit.SECONDS))
      result === true
    }

    "Support updating a room topic" in {
      val result = Await.result(updateRoomTopic(roomId, "blah"), Duration(1, TimeUnit.SECONDS))
      result === true
    }

    "Support posting a message" in {
      val result = Await.result(speak(roomId, "this is a test message"), Duration(1, TimeUnit.SECONDS))
      result === true
    }

    "Support recent messages" in {
      val result = Await.result(recentMessages(roomId), Duration(1, TimeUnit.SECONDS))
      result.map(_.id) === List(1,2,3,4,5)
    }
  }



  val client = new Client {
    lazy val firstRoom = Json.obj("room" -> (Json.parse(roomsArtifact) \ "rooms")(0))

    def GET(path: String) = path match {
      case "/account.json"           => Future.successful(Right(accountArtifact.getBytes))
      case "/presence.json"          => Future.successful(Right(roomsArtifact.getBytes))
      case "/room/22222/recent.json" => Future.successful(Right(messagesArtifact.getBytes))
      case "/rooms.json"             => Future.successful(Right(roomsArtifact.getBytes))
      case "/room/22222.json"        => Future.successful(Right(firstRoom.toString.getBytes))
      case "/users/me.json"          => Future.successful(Right(meArtifact.getBytes))
      case "/users/5555.json"        => Future.successful(Right(userArtifact.getBytes))
      case _                         => Future.successful(Left("Not Found"))
    }

    def POST(path: String, body: Array[Byte] = Array(), contentType: String = "application/json") = path match {
      case "/room/22222/join.json"   => Future.successful(Right(Array()))
      case "/room/22222/leave.json"  => Future.successful(Right(Array()))
      case "/room/22222/speak.json"  => Future.successful(Right(Array()))
      case _                         => Future.successful(Left("Not Found"))
    }

    def PUT(path: String, body: Array[Byte] = Array(), contentType: String = "application/json") = path match {
      case "/room/22222.json" => Future.successful(Right(Array()))
      case _                  => Future.successful(Left("Not Found"))

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
          "id": 1,
          "body": null,
          "user_id": null
        },
        {
          "type": "EnterMessage",
          "room_id": 33333,
          "created_at": "2011/09/13 15:44:33 +0000",
          "id": 2,
          "body": null,
          "user_id": 12345
        },
        {
          "type": "TextMessage",
          "room_id": 33333,
          "created_at": "2011/09/14 16:33:21 +0000",
          "id": 3,
          "body": "anyone still having problems getting into the room?",
          "user_id": 45347
        },
        {
          "type": "TextMessage",
          "room_id": 33333,
          "created_at": "2011/09/14 17:04:03 +0000",
          "id": 4,
          "body": "i am guessing some people are just not used to signing in and using this tool",
          "user_id": 23423423
        },
        {
          "type": "KickMessage",
          "room_id": 33333,
          "created_at": "2011/09/14 17:40:19 +0000",
          "id": 5,
          "body": null,
          "user_id": 935596
        }
      ]
    }
    """
}

