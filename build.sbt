name := "bivouac"

version := "1.0"

scalaVersion := "2.9.2"

resolvers ++= Seq(
  "Sonatype" at "http://oss.sonatype.org/content/repositories/public",
  "Typesafe" at "http://repo.typesafe.com/typesafe/releases/"
)

libraryDependencies ++= Seq(
  "com.github.jdegoes" % "blueeyes-core_2.9.1"  % "0.6.1-SNAPSHOT",
  "com.github.jdegoes" % "blueeyes-json_2.9.1"  % "0.6.1-SNAPSHOT",
  "ch.qos.logback"     % "logback-classic"      % "1.0.0"  % "runtime",
  "org.scalaz"         %  "scalaz-core_2.9.2"   % "7.0-SNAPSHOT" changing(),
  "org.specs2"         % "specs2_2.9.2"         % "1.12.3" % "test"
)