package com.gu.conf

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.gu.AwsIdentity
import com.typesafe.config.{Config, ConfigFactory, ConfigParseOptions}
import org.slf4j.LoggerFactory

import scala.io.Source

case class S3ConfigurationLocation(
  bucket: String,
  path: String,
  region: String = Regions.getCurrentRegion.getName
) extends ConfigurationLocation {

  private val logger = LoggerFactory.getLogger(this.getClass)

  override def load(credentials: => AWSCredentialsProvider): Config = {
    logger.info(s"Attempting to load configuration from S3 for bucket = $bucket path = $path and region = $region")
    val s3Client = {
      val builder = AmazonS3ClientBuilder.standard()
      builder.setCredentials(credentials)
      builder.setRegion(region)
      builder.build()
    }

    val s3Object = s3Client.getObject(bucket, path)
    val content = Source.fromInputStream(s3Object.getObjectContent).mkString

    s3Client.shutdown()

    ConfigFactory.parseString(content, ConfigParseOptions.defaults().setOriginDescription(s"s3://$bucket/$path"))
  }
}

object S3ConfigurationLocation {
  def default(identity: AwsIdentity): ConfigurationLocation = {
    S3ConfigurationLocation(s"${identity.app}-dist", s"${identity.stage}/${identity.stack}/${identity.app}/${identity.app}.conf")
  }
}
