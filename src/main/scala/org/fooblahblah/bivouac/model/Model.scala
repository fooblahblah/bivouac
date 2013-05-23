package org.fooblahblah.bivouac.model

import java.util.Date
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.json.util._
import play.api.libs.functional.syntax._


object Model {
  case class CampfireConfig(token: String, domain: String)

  case class Account(id: Int, name: String, subDomain: String, plan: String, ownerId: Int, timezone: String, createdAt: Date, updatedAt: Date)

  case class Message(id: Int, roomId: Int, userId: Option[Int], messageType: String, body: Option[String], createdAt: Date)

  case class Room(id: Int, name: String, topic: String, membershipLimit: Int, locked: Boolean, createdAt: Date, updatedAt: Date, users: Option[List[User]] = None)

  case class Speak(message: String) {
    def toJSON = Json.obj("message" -> Json.obj("body" -> JsString(message)))
  }

  case class User(id: Int, name: String, email: String, admin: Boolean, avatarUrl: String, userType: String, createdAt: Date)

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
    (__ \ "users").readNullable(list[User]))(Room)

  implicit val listRoomReads: Reads[List[Room]] = ((__ \ "rooms").read(list[Room]))


  implicit val messageReads: Reads[Message] = (
    (__ \ "id").read[Int] ~
    (__ \ "room_id").read[Int] ~
    (__ \ "user_id").readNullable[Int] ~
    (__ \ "type").read[String] ~
    (__ \ "body").readNullable[String] ~
    (__ \ "created_at").read[Date](customDateReads))(Message)

  implicit val listMessageReads: Reads[List[Message]] = ((__ \ "messages").read(list[Message]))
}
