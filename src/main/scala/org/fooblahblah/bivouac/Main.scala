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

    for {
      _ <- printMe
//      _ <- printRooms
      _ <- printRoom(497180)
      _ <- Future(println("terminating..."))
    } yield sys.exit
  }
}
