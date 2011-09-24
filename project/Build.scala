import sbt._
import Keys._
import Package._

object BuildSettings {
  val buildOrganization = "org.fooblahblah"
  val buildVersion      = "0.1.0"
  val buildScalaVersion = "2.9.1"

  val buildSettings = Defaults.defaultSettings ++ Seq (
    organization := buildOrganization,
    version      := buildVersion,
    scalaVersion := buildScalaVersion
  )
}

object Resolvers {
  val customResolvers = Seq(
    "Scala Repo Releases"        at "http://scala-tools.org/repo-releases/",
    "Scala-tools.org Repository" at "http://scala-tools.org/repo-snapshots/",
    "JBoss Releases"             at "http://repository.jboss.org/nexus/content/groups/public/",
    "Sonatype Releases"          at "http://oss.sonatype.org/content/repositories/releases",
    "Nexus Scala Tools"          at "http://nexus.scala-tools.org/content/repositories/releases",
    "Akka Maven2 Repository"     at "http://akka.io/repository/",
    "Guiceyfruit Googlecode"     at "http://guiceyfruit.googlecode.com/svn/repo/releases/",
    "Guice Maven"                at "http://guice-maven.googlecode.com/svn/trunk")
}

object Dependencies {
  val commonDeps =  Seq(
    "org.scala-tools.testing" %  "specs_2.9.0-1" % "1.6.8"  % "compile",
    "junit"                   %  "junit"         % "4.8.2"  % "compile")
}

object BivouacBuild extends Build {
  import Dependencies._
  import Resolvers._
  import BuildSettings._

  lazy val blueeyes = RootProject(uri("git://github.com/jdegoes/blueeyes.git"))

  lazy val bivouac  = Project("root", file("."), settings = buildSettings ++ Seq(libraryDependencies := commonDeps, resolvers ++= customResolvers)) dependsOn(blueeyes)
}

