package org.fooblahblah.bivouac

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object TestApp {
  val client = Bivouac()

  def main(args: Array[String]) {
    import client._

    def printRooms: Future[Unit] =
      rooms map { rooms =>
        rooms foreach { r =>
          println(r.id + " " + r.name)
        }
      }

    def printMe: Future[Unit] =
      me map (println)

    def printRoom(roomId: Int) =
      room(roomId) map (println)

    def printRecent(roomId: Int) =
      recentMessages(roomId) map (println)

//    val roomId = 399408 // Test
//    val roomId = 537356 // data_engineering
    val roomId = 454626 // Boulder

    for {
//      _ <- printMe
//      _ <- printRooms
      _ <- printRoom(roomId)
//      _ <- printRecent(497180)
      _ <- leave(roomId)
      _ <- join(roomId)
      _ <- live(roomId, println)
    } yield true //sys.exit
  }
}
