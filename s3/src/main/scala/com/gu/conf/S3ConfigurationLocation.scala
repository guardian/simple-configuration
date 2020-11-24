package com.gu.conf

import java.nio.charset.StandardCharsets.UTF_8

import com.gu.AwsIdentity
import com.typesafe.config.{Config, ConfigFactory, ConfigParseOptions}
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.regions.internal.util.EC2MetadataUtils
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest

case class S3ConfigurationLocation(
  bucket: String,
  path: String,
  region: String = EC2MetadataUtils.getEC2InstanceRegion
) extends ConfigurationLocation {

  private val logger = LoggerFactory.getLogger(this.getClass)

  override def load(credentials: => AwsCredentialsProvider): Config = {
    logger.info(s"Attempting to load configuration from S3 for bucket = $bucket path = $path and region = $region")
    val s3Client = S3Client.builder
      .credentialsProvider(credentials)
      .region(Region.of(region))
      .build()

    val content = s3Client.getObjectAsBytes(
        GetObjectRequest.builder.bucket(bucket).key(path).build()
      ).asString(UTF_8)

    s3Client.close()

    ConfigFactory.parseString(content, ConfigParseOptions.defaults().setOriginDescription(s"s3://$bucket/$path"))
  }
}

object S3ConfigurationLocation {
  def default(identity: AwsIdentity): ConfigurationLocation = {
    S3ConfigurationLocation(s"${identity.app}-dist", s"${identity.stage}/${identity.stack}/${identity.app}/${identity.app}.conf")
  }
}
