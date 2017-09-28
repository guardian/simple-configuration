package com.gu.conf

import java.io.File

sealed trait ConfigurationLocation

case class S3ConfigurationLocation(
  bucket: String,
  path: String
) extends ConfigurationLocation

case class FileConfigurationLocation(file: File) extends ConfigurationLocation

case class ResourceConfigurationLocation(resourceName: String) extends ConfigurationLocation
