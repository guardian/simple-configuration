package com.gu

import com.amazonaws.auth.{AWSCredentialsProvider, DefaultAWSCredentialsProviderChain}
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.ec2.{AmazonEC2, AmazonEC2ClientBuilder}
import com.amazonaws.services.ec2.model.{DescribeTagsRequest, Filter}
import com.amazonaws.util.EC2MetadataUtils
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

sealed trait AppIdentity

case class AwsIdentity(
  app: String,
  stack: String,
  stage: String,
  region: String
) extends AppIdentity

case class DevIdentity(
  app: String
) extends AppIdentity

object AppIdentity {
  private val logger = LoggerFactory.getLogger(this.getClass)

  private def safeAwsOperation[A](errorMessage: => String)(operation: => A): Option[A] = Try(operation) match {
    case Success(value) => Option(value)
    case Failure(e) =>
      logger.error(errorMessage, e)
      None
  }

  private def fromEC2Tags(credentials: => AWSCredentialsProvider): Option[AppIdentity] = {

    def listTags(instanceId: String, ec2Client: AmazonEC2): Map[String, String] = {
      val tags = safeAwsOperation(s"Failed to describe the tags of the instance $instanceId") {
        val result = ec2Client.describeTags(new DescribeTagsRequest().withFilters(
          new Filter("resource-type").withValues("instance"),
          new Filter("resource-id").withValues(instanceId)
        ))
        result.getTags.asScala.map{td => td.getKey -> td.getValue }.toMap
      }
      tags.getOrElse(Map.empty)
    }

    def ec2Client(region: Region, credentials: AWSCredentialsProvider): AmazonEC2 = {
      val builder = AmazonEC2ClientBuilder.standard()
      builder.setRegion(region.getName)
      builder.setCredentials(credentials)
      builder.build()
    }

    val region = safeAwsOperation("Failed to identify the regionName of the instance")(Regions.getCurrentRegion)
    val tags = for {
      awsRegion <- region
      client <- safeAwsOperation("Failed to instantiate ec2 client")(ec2Client(awsRegion, credentials))
      instanceId <- safeAwsOperation("Failed to identify the instanceId")(EC2MetadataUtils.getInstanceId)
    } yield {
      val result = listTags(instanceId, client)
      client.shutdown()
      result
    }

    val allTags = tags.getOrElse(Map.empty)

    for {
      regionName <- region.map(_.getName)
      app <- allTags.get("App")
      stack <- allTags.get("Stack")
      stage <- allTags.get("Stage")
    } yield AwsIdentity(
      app = app,
      stack = stack,
      stage = stage,
      region = regionName
    )
  }

  private def fromLambdaEnvVariables(): Option[AppIdentity] = {
    def getEnv(variableName: String): Option[String] = Option(System.getenv(variableName))
    for {
      app <- getEnv("App")
      stack <- getEnv("Stack")
      stage <- getEnv("Stage")
      region <- getEnv("AWS_DEFAULT_REGION")
    } yield AwsIdentity(
      app = app,
      stack = stack,
      stage = stage,
      region = region
    )
  }

  def region: String = {
    lazy val ec2Region = safeAwsOperation("Failed to identify the regionName of the instance") {
      Option(Regions.getCurrentRegion).map(_.getName)
    }.flatten
    lazy val lambdaRegion = Option(System.getenv("AWS_DEFAULT_REGION"))
    lambdaRegion orElse ec2Region getOrElse "eu-west-1"
  }

  def whoAmI(
    defaultAppName: String,
    credentials: => AWSCredentialsProvider = DefaultAWSCredentialsProviderChain.getInstance
  ): AppIdentity = {
    val result = fromLambdaEnvVariables orElse fromEC2Tags(credentials) getOrElse DevIdentity(defaultAppName)
    logger.info(s"Detected the following AppIdentity: $result")
    result
  }
}