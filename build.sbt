name := "simple-s3-configuration"
organization := "com.gu"

scalaVersion := "2.11.11"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-s3" % "1.11.204",
  "com.amazonaws" % "aws-java-sdk-ec2" % "1.11.204",
  "com.typesafe" % "config" % "1.3.1",
  "org.slf4j" % "slf4j-api" % "1.7.25"
)

publishMavenStyle := false
bintrayOrganization := Some("guardian")
bintrayRepository := "platforms"

licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html"))

// Release
import ReleaseTransformations._
releasePublishArtifactsAction := PgpKeys.publishSigned.value
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  publishArtifacts,
  releaseStepTask(bintrayRelease),
  setNextVersion,
  commitNextVersion,
  pushChanges
)