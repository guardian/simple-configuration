package com.gu.conf

import java.io.File

import com.amazonaws.regions.Regions

sealed trait ConfigurationLocation

case class S3ConfigurationLocation(
  bucket: String,
  path: String,
  region: String = Regions.getCurrentRegion.getName
) extends ConfigurationLocation

case class FileConfigurationLocation(file: File) extends ConfigurationLocation

case class ResourceConfigurationLocation(resourceName: String) extends ConfigurationLocation
