package com.gu.conf

import java.io.File

import com.amazonaws.auth.{AWSCredentialsProvider, DefaultAWSCredentialsProviderChain}
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.gu.AppIdentity
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory

import scala.io.Source

object ConfigurationLoader {

  private val logger = LoggerFactory.getLogger(this.getClass)

  private def fromS3(s3Location: S3ConfigurationLocation, identity: AppIdentity, credentials: => AWSCredentialsProvider): Config = {
    val s3Client = {
      val builder = AmazonS3ClientBuilder.standard()
      builder.setCredentials(credentials)
      builder.setRegion(identity.region)
      builder.build()
    }

    logger.info(s"Fetching configuration for $identity")

    val s3Object = s3Client.getObject(s3Location.bucket, s3Location.path)
    val content = Source.fromInputStream(s3Object.getObjectContent).mkString

    s3Client.shutdown()

    ConfigFactory.parseString(content)
  }

  private def fromResource(resourceLocation: ResourceConfigurationLocation): Config =
    ConfigFactory.parseResources(resourceLocation.resourceName)

  private def fromFile(fileLocation: FileConfigurationLocation): Config =
    ConfigFactory.parseFile(fileLocation.file)

  private def defaultDevLocation(identity: AppIdentity): ConfigurationLocation = {
    val home = System.getProperty("user.home")
    FileConfigurationLocation(new File(s"$home/.gu/${identity.app}.conf"))
  }

  private def defaultS3Location(identity: AppIdentity): ConfigurationLocation = {
    S3ConfigurationLocation(s"${identity.app}-dist", s"${identity.stage}/${identity.stack}/${identity.app}/${identity.app}.conf")
  }

  def load(
    identity: AppIdentity,
    credentials: => AWSCredentialsProvider = DefaultAWSCredentialsProviderChain.getInstance
  )(locationFunction: PartialFunction[AppIdentity, ConfigurationLocation] = PartialFunction.empty): Config = {
    val getLocation = locationFunction.orElse[AppIdentity, ConfigurationLocation] {
      case AppIdentity(_, _, "DEV", _) => defaultDevLocation(identity)
      case _ => defaultS3Location(identity)
    }

    getLocation(identity) match {
      case s3Location: S3ConfigurationLocation => fromS3(s3Location, identity, credentials)
      case fileLocation: FileConfigurationLocation => fromFile(fileLocation)
      case resourceLocation: ResourceConfigurationLocation => fromResource(resourceLocation)
    }
  }
}
