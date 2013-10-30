import sbt.Keys._
import sbt._

object OurBuild  extends Build {
  override lazy val settings = super.settings ++ Seq(
    organization := "com.loyal3",
    version := Option(System.getenv("BUILD_VCS_NUMBER")).getOrElse("DEV"),
    scalaVersion := "2.9.3",
    sbtPlugin := true,
    publishTo := Option(System.getenv("PUBLISH_REPOSITORY_URL")).map("remote" at),
    publishMavenStyle := false,
    creds
  )

  lazy val root = Project(
    id = "sbt-package-deb",
    base = file("."),
    settings = Project.defaultSettings
  )

  lazy val creds = credentials ++= (for {
    realmName <- Option(System.getenv("CREDENTIALS_REALM_NAME"))
    realmUrl  <- Option(System.getenv("CREDENTIALS_REALM_URL"))
    username  <- Option(System.getenv("CREDENTIALS_USERNAME"))
    password  <- Option(System.getenv("CREDENTIALS_PASSWORD"))
  } yield {
    Credentials(realmName, realmUrl, username, password)
  }).toSeq
}