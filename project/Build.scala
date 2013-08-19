import sbt._
import Keys._

object BivouacBuild extends Build {

  lazy val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := "org.fooblahblah",
    version      := "1.1.0",
    scalaVersion := "2.10.2",

    resolvers ++= Seq(
      "typesafe repo" at "http://repo.typesafe.com/typesafe/maven-releases"
    ),

    libraryDependencies ++= Seq(
      "ch.qos.logback"              % "logback-classic"           % "1.0.13",
      "com.typesafe"                %  "config"                   % "1.0.0",
      "junit"                       %  "junit"                    % "4.11",
      "net.databinder.dispatch"     %% "dispatch-core"            % "0.11.0",
      "org.apache.directory.studio" %  "org.apache.commons.codec" % "1.6",
      "org.specs2"                  %% "specs2"                   % "1.13" % "test",
      "org.slf4j"                   %  "slf4j-api"                % "1.7.5"
    ),
    scalacOptions ++= Seq("-language:postfixOps", "-language:implicitConversions")
  )

  lazy val playJson = RootProject(uri("https://github.com/victorops/play-json.git#abf0ea9dcb23a498cfae4fcd0dc06fb07e05b474"))

  lazy val root = Project(id = "bivouac", base = file("."), settings = buildSettings) dependsOn (playJson)
}
