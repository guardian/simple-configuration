package com.gu

import com.amazonaws.auth.{AWSCredentialsProvider, DefaultAWSCredentialsProviderChain}
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.ec2.{AmazonEC2, AmazonEC2ClientBuilder}
import com.amazonaws.services.ec2.model.{DescribeTagsRequest, Filter}
import com.amazonaws.util.EC2MetadataUtils
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

case class AppIdentity(
  app: String,
  stack: String,
  stage: String,
  region: String
)

object AppIdentity {
  private val logger = LoggerFactory.getLogger(this.getClass)

  private def safeAwsOperation[A](errorMessage: => String)(operation: => A): Option[A] = Try(operation) match {
    case Success(value) => Option(value)
    case Failure(e) =>
      logger.error(errorMessage, e)
      None
  }

  private def listTags(instanceId: String, ec2Client: AmazonEC2): Map[String, String] = {
    val tags = safeAwsOperation(s"Failed to describe the tags of the instance $instanceId") {
      val result = ec2Client.describeTags(new DescribeTagsRequest().withFilters(
        new Filter("resource-type").withValues("instance"),
        new Filter("resource-id").withValues(instanceId)
      ))
      result.getTags.asScala.map{td => td.getKey -> td.getValue }.toMap
    }
    tags.getOrElse(Map.empty)
  }

  private def ec2Client(region: Region, credentials: AWSCredentialsProvider): AmazonEC2 = {
    val builder = AmazonEC2ClientBuilder.standard()
    builder.setRegion(region.getName)
    builder.setCredentials(credentials)
    builder.build()
  }

  def whoAmI(
    defaultAppName: String,
    defaultStackName: String,
    credentials: => AWSCredentialsProvider = DefaultAWSCredentialsProviderChain.getInstance
  ): AppIdentity = {
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

    AppIdentity(
      app = allTags.getOrElse("App", defaultAppName),
      stack = allTags.getOrElse("Stack", defaultStackName),
      stage = allTags.getOrElse("Stage", "DEV"),
      region = region.map(_.getName).getOrElse("eu-west-1")
    )
  }
}