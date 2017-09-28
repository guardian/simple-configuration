name := "simple-s3-configuration"
organization := "com.gu"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.11"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-s3" % "1.11.204",
  "com.amazonaws" % "aws-java-sdk-ec2" % "1.11.204",
  "com.typesafe" % "config" % "1.3.1",
  "org.slf4j" % "slf4j-api" % "1.7.25"
)