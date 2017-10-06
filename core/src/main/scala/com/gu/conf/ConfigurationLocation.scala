package com.gu.conf

import java.io.File

import com.amazonaws.auth.AWSCredentialsProvider
import com.typesafe.config.{Config, ConfigFactory}

trait ConfigurationLocation {
  def load(credentials: => AWSCredentialsProvider): Config
}

case class FileConfigurationLocation(file: File) extends ConfigurationLocation {
  override def load(credentials: => AWSCredentialsProvider): Config = ConfigFactory.parseFile(file)
}

case class ResourceConfigurationLocation(resourceName: String) extends ConfigurationLocation {
  override def load(credentials: => AWSCredentialsProvider): Config = ConfigFactory.parseResources(resourceName)
}
