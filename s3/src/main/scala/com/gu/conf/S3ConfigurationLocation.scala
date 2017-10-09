package com.gu.conf

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.gu.AwsIdentity
import com.typesafe.config.{Config, ConfigFactory}

import scala.io.Source

case class S3ConfigurationLocation(
  bucket: String,
  path: String,
  region: String = Regions.getCurrentRegion.getName
) extends ConfigurationLocation {

  override def load(credentials: => AWSCredentialsProvider): Config = {
    val s3Client = {
      val builder = AmazonS3ClientBuilder.standard()
      builder.setCredentials(credentials)
      builder.setRegion(region)
      builder.build()
    }

    val s3Object = s3Client.getObject(bucket, path)
    val content = Source.fromInputStream(s3Object.getObjectContent).mkString

    s3Client.shutdown()

    ConfigFactory.parseString(content)
  }
}

object S3ConfigurationLocation {
  def default(identity: AwsIdentity): ConfigurationLocation = {
    S3ConfigurationLocation(s"${identity.app}-dist", s"${identity.stage}/${identity.stack}/${identity.app}/${identity.app}.conf")
  }
}