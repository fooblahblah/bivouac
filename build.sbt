seq(com.github.retronym.SbtOneJar.oneJarSettings: _*)

name := "bivouac"

organization := "org.fooblahblah"

version := "1.0.0"

scalaVersion := "2.10.0-RC3"

resolvers ++= Seq(
  "typesafe repo" at "http://repo.typesafe.com/typesafe/maven-releases",
  "spray repo" at "http://repo.spray.io"
)

libraryDependencies ++= Seq(
  "com.typesafe"                %  "config"                   % "1.0.0",
  "com.typesafe.akka"           %  "akka-actor_2.10.0-RC3"    % "2.1.0-RC3",
  "io.spray"                    %  "spray-client"             % "1.1-M6",
  "junit"                       %  "junit"                    % "4.11",
  "org.apache.directory.studio" %  "org.apache.commons.codec" % "1.6",
  "org.specs2"                  %  "specs2_2.10.0-RC3"        % "1.12.3",
  "org.scalaz"                  %  "scalaz_2.10.0-RC3"        % "7.0.0-M5",
  "play"                        %% "play"                    % "2.1-RC1"
)
