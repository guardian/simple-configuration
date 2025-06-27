package com.gu.conf

import com.gu.{AppIdentity, AwsIdentity}
import com.typesafe.config.Config
import software.amazon.awssdk.auth.credentials.{
  AwsCredentialsProvider,
  DefaultCredentialsProvider
}

object SSMConfigurationLoader {

  def load(
    identity: AppIdentity,
    credentials: => AwsCredentialsProvider = DefaultCredentialsProvider.create()
  ): Config =
    ConfigurationLoader.load(identity, credentials) {
      case awsIdentity: AwsIdentity => SSMConfigurationLocation.default(awsIdentity)
    }

}
