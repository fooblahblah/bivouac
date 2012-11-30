package org.fooblahblah.bivouac

import akka.dispatch.Future
import blueeyes.bkka.AkkaDefaults

object TestApp extends Bivouac {
  var apiKey: Option[String] = None
  var domain: Option[String] = None

  lazy val config = CampfireConfig(
    apiKey.getOrElse(sys.error("apiKey was uninitialized")),
    domain.getOrElse(sys.error("domain was uninitialized")))

  def main(args: Array[String]) {
    assert(args.length == 2)

    apiKey = Some(args(0))
    domain = Some(args(1))

    def printRooms: Future[Unit] =
      rooms map { rooms =>
        rooms foreach { r =>
          println(r.id + " " + r.name)
        }
      }

    def printAccount: Future[Unit] =
      account map (println)

    for {
      _ <- printRooms
      // _ <- printAccount
      _ <- Future(println("terminating..."))
      _ <- Future(AkkaDefaults.actorSystem.shutdown())
    } yield sys.exit
  }
}
