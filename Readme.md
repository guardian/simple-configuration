# simple-configuration

[ ![Download](https://api.bintray.com/packages/guardian/platforms/simple-configuration-core/images/download.svg) ](https://bintray.com/guardian/platforms/simple-configuration-core/_latestVersion)

_A configuration library without any magic_

## Releasing

Run `sbt release` at the root to publish the artifacts to sonatype. For instructions on how to set up publishing, visit [this doc](https://docs.google.com/document/d/1rNXjoZDqZMsQblOVXPAIIOMWuwUKe3KzTCttuqS7AcY/edit).

[!IMPORTANT]
For some reason, releases are not promoted from staging, so you will need to do this manually if you notice your package sitting in [staging](https://oss.sonatype.org/#stagingRepositories). First 'close' the staged package, then choose 'release' and this will promote it to Maven.
Our investigations imply that the `releaseStepCommand("sonatypeBundleRelease")` step is not running during release, but this has not been fully investigated for time reasons.

## Goal
This library will help you load the configuration of your application from S3 or the SSM parameter store.

It relies on [lightbend's configuration library](https://github.com/typesafehub/config), AWS' S3 SDK, SSM SDK and EC2 SDK.

## Usage

In your `build.sbt`:
```scala
libraryDependencies += "com.gu" %% "simple-configuration-s3" % "1.5.7"
// OR
libraryDependencies += "com.gu" %% "simple-configuration-ssm" % "1.5.7"
```

Then in your code:

```scala
import com.gu.{AppIdentity, AwsIdentity}
import com.gu.conf.{ConfigurationLoader, S3ConfigurationLocation}
import com.typesafe.config.Config

val CredentialsProvider = DefaultCredentialsProvider.create()
val isDev = context.environment.mode == Mode.Dev
val config =
  for {
    identity <- if (isDev)
      Success(DevIdentity("support-frontend"))
   else
     AppIdentity.whoAmI(defaultAppName = "support-frontend", CredentialsProvider)
   config <- Try(ConfigurationLoader.load(identity, CredentialsProvider) {
     case identity: AwsIdentity => S3ConfigurationLocation.default(identity)
   }
  } yield config
```

Let's look in detail at what's happening here.

### AppIdentity.whoAmI (optional)
The `AppIdentity.whoAmI` function is a helper that will try to identify your application via the tags (`App`, `Stack`, `Stage`) set on the ec2 instance you are running, or via the environment variables you set on your lambda (`App`, `Stack`, `Stage`, and the one provided by AWS `AWS_DEFAULT_REGION`). It will need the appropriate IAM permission to be able to query the ec2 API (see [IAM paragraph below](#iam-permissions))

If you are running your application on an ec2 instance or a lambda, the function will return an AppIdentity subtype: AwsIdentity defined as follows:

```scala
case class AwsIdentity(
  app: String,
  stack: String,
  stage: String,
  region: String
) extends AppIdentity
```

If you are not running on an ec2 instance or a lambda - for instance when testing locally - the function will return a failed Try once the AWS call times out.  This causes a delay when starting the app locally, so it's recommended to create the DevIdentity yourself if you're running in DEV.

If you don't need to auto-detect the identity of your application, you can instantiate an AppIdentity yourself and provide the values you want.

You can optionally provide your [own AWS credentials](#examples) rather than relying on the defaults if you were to prefer controlling that aspect. It is defined liked that:

```scala
def whoAmI(
  defaultAppName: String,
  credentials: => AWSCredentialsProvider = DefaultAWSCredentialsProviderChain.getInstance
): AppIdentity
```

### ConfigurationLoader.load

This function will load your configuration from its source (S3, file, SSM), or locally if you are in dev mode.
It will use the identity to understand where the app is running, and load the configuration accordingly. It will of course need the appropriate IAM permission, as defined in the [paragraph below](#iam-permissions).

By default the configuration are loaded from the following locations:

`~/.gu/${identity.app}.conf` for the local file if you are in dev mode (AppIdentity is of type DevIdentity)

`s3://${identity.app}-dist/${identity.stage}/${identity.stack}/${identity.app}/${identity.app}.conf` once running on an EC2 instance or

`/${identity.stage}/${identity.stack}/${identity.app}/*` if you're loading it from the SSM parameter store

`ConfigurationLoader.load` is defined like that:
```scala
def load(
  identity: AppIdentity,
  credentials: => AWSCredentialsProvider = DefaultAWSCredentialsProviderChain.getInstance
)(locationFunction: PartialFunction[AppIdentity, ConfigurationLocation] = PartialFunction.empty): Config
```

The only parameter you need to provide is the identity, other parameters will use a sensible default.

`identity`: identity is a parameter of type `AppIdentity` that describes your application (name, stack, stage, awsRegion). See [above paragraph](#appidentitywhoami-optional) about AppIdentity.whoAmI

`credentials`: These are the AWS credentials that will be used to load your configuration from S3. The default behaviour should be enough, but if you wanted to customise the way the credentials are picked you could pass it as a parameter. Note that it's a pass-by-name parameter so the content won't be evaluated unless needed. The default behaviour when running locally is to load the configuration from a local file, so credentials won't be evaluated in that case.

`locationFunction`: This function is a way to customise where to load the configuration depending on the environment. For instance if your configuration is in the same place for two different stacks or if you're using the same configuration file for multiple apps (multi-module project) you could override the path that will be used. It's a partial function, so it's thought to be used as pattern matching on the `AppIdentity` you provided. You can see an [example below](#examples) or you can see what return types are possible in the [Location Types paragraph](#location-types).

## Examples

### Provide your own credentials
```scala
val identity = AppIdentity.whoAmI(defaultAppName = "mobile-apps-api", credentials = myOwnCredentials)
val config = ConfigurationLoader.load(identity, credentials = myOwnCredentials) {
  case identity: AwsIdentity => S3ConfigurationLocation.default(identity)
}
```

### Custom S3 location
See [Location Types](#location-types) for a list of all the location types.

```scala
val config = ConfigurationLoader.load(identity) {
  case AwsIdentity(app, "stack1", stage, _) => S3ConfigurationLocation("mybucket", s"somepath/$stage/$app.conf")
  case DevIdentity(myApp) => ResourceConfigurationLocation(s"localdev-${myApp}.conf")
}
```

### Custom SSM location
See [Location Types](#location-types) for a list of all the location types.

```scala
val config = ConfigurationLoader.load(identity) {
  case AwsIdentity(app, "stack1", stage, _) => SSMConfigurationLocation(s"/myAPI/$stage")
  case DevIdentity(myApp) => ResourceConfigurationLocation(s"localdev-${myApp}.conf")
}
```

### Play application with Compile time Dependency Injection
```scala
import play.api.Configuration
import com.gu.AppIdentity
import com.gu.conf.ConfigurationLoader


class MyApplicationLoader extends ApplicationLoader {
  override def load(context: Context): Application = {
    LoggerConfigurator(context.environment.classLoader) foreach { _.configure(context.environment) }
    val identity = AppIdentity.whoAmI(defaultAppName = "myApp")
    val loadedConfig = ConfigurationLoader.load(identity) {
      case identity: AwsIdentity => S3ConfigurationLocation.default(identity)
    }
    val newContext = context.copy(initialConfiguration = Configuration(loadedConfig).withFallback(context.initialConfiguration))
    (new BuiltInComponentsFromContext(newContext) with AppComponents).application
  }
}
```

Here's what we're doing above:
 - Initialise the logs (standard behaviour when using compile time dependencies with Play)
 - Auto detect the application identity
 - Load the Lightbend (Typesafe) Config
 - Wrap the loaded config in a Play configuration and concatenate the initial play configuration (application.conf) with what has been loaded from S3 or locally
 - Use the resulting configuration to instantiate the Play app

## Location types

When providing your own mapping between `AppIdentity` and location, you can specify five location types:

- `S3ConfigurationLocation(bucket: String, path: String, region: String)`
- `SSMConfigurationLocation(path: String, region: String)`
- `FileConfigurationLocation(file: File)`
- `ResourceConfigurationLocation(resourceName: String)`
- `ComposedConfigurationLocation(locations: List[ConfigurationLocation])`

### S3ConfigurationLocation
This will help `ConfigurationLoader.load` locate the file on an S3 bucket. You must provide the bucket name and the complete path to the file. The region defaults to the autodetected one, but you can override it if you please. The file fetched from S3 is expected to be a valid typesafe config file (HOCON, json or properties)

### SSMConfigurationLocation
This will help `ConfigurationLoader.load` select all the parameters in the SSM parameter store. The path must start with a / and should not finish with one. Here's a path example `"/this/is/valid"`.
Parameters can be encrypted and are named following this convention: `"/path/my.typesafe.config.key"`. Here's an example: `/PROD/mobile/mobile-fronts/apis.capi.timeout`

### FileConfigurationLocation
This will be useful when loading a file ouside of your classpath. Typically, a configuration that can contain secrets and that shouldn't be committed to the repository. This is used by default when in DEV mode and points to `~/.gu/${identity.app}.conf`

### ResourceConfigurationLocation
This will load a configuration file from within your classpath. Typically a file under the `resource` folder of your project. It is useful if your configuration can be committed to your repo and is directly accessible from the classpath. 

### ComposedConfigurationLocation
Composes a list of `ConfigurationLocation`s. Configurations earlier in the list take precedence over ones later in the list.

## IAM permissions
### When using AppIdentity.whoAmI on an EC2 instance
_Note that you won't need it for a lambda as these are passed as environment variables_
```json
{
    "Effect": "Allow",
    "Action": [ 
      "autoscaling:DescribeAutoScalingInstances",
      "autoscaling:DescribeAutoScalingGroups",
      "ec2:DescribeTags"
    ],
    "Resource": "*"
}
```
### When loading the configuration from S3
```json
{
    "Effect": "Allow",
    "Action": "s3:GetObject",
    "Resource": "arn:aws:s3:::mybucket/*"
}
```
### When loading the configuration from SSM
```json
{
    "Effect": "Allow",
    "Action": "ssm:GetParametersByPath",
    "Resource": "arn:aws:ssm:${AWS::Region}:${AWS::AccountId}:parameter/path/used"
}
```
or when using cloudformation
```yaml
  - Action:
    - ssm:GetParametersByPath
    Effect: Allow
    Resource: !Sub arn:aws:ssm:${AWS::Region}:${AWS::AccountId}:parameter/${Stage}/${Stack}/${App}
```
