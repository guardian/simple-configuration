# simple-s3-configuration

_A configuration library without any magic_

## Goal
This library will help you load the configuration of your application from s3.

It relies on [lightbend's configuration library](https://github.com/typesafehub/config), AWS' s3 sdk and AWS' ec2 sdk.

## Usage

To load a configuration you can provide your own AWS credentials, or rely on the default value (DefaultAWSCredentialsProviderChain).

```scala
val identity = AppIdentity.whoAmI(defaultAppName = "mobile-apps-api", defaultStackName = "mobile")
val config = ConfigurationLoader.load(identity)()
```

Let's look in detail at what's happening here.

### AppIdentity.whoAmI (optional)
The `AppIdentity.whoAmI` function is a helper that will try to identify your application via the tags (`App`, `Stack`, `Stage`) set on the ec2 instance you are running. It will need the appropriate IAM permission to be able to query the ec2 API (see paragraph below)

If you are not running on an ec2 instance, for instance when testing locally, the function will use the default  values you provided.

It will return you an object of type AppIdentity, defined as follow:

```scala
case class AppIdentity(
  app: String,
  stack: String,
  stage: String,
  region: String
)
```

If you don't need to auto-detect the identity of your application yourself, you can instantiate an AppIdentity yourself and provide the values you want.

### ConfigurationLoader.load

This function will load your configuration from S3, or locally if you are in dev mode.
It will use the identity to detect if the stage is "DEV", and load the configuration accordingly. It will of course need the appropriate IAM permission, as defined in the paragraph bellow.

By default the configuration are loaded from the following locations:

`~/.gu/${identity.app}.conf` for the local file if you are in "DEV" mode (AppIdentity.stage == "DEV")

`s3://${identity.app}-dist/${identity.stage}/${identity.stack}/${identity.app}/${identity.app}.conf` once running on an EC2 instance

`ConfigurationLoader.load` is defined like that:
```scala
def load(
    identity: AppIdentity,
    credentials: => AWSCredentialsProvider = DefaultAWSCredentialsProviderChain.getInstance
  )(locationFunction: PartialFunction[AppIdentity, ConfigurationLocation] = PartialFunction.empty): Config
```

The only parameter you need to provide is the identity, other parameters will use a sensible default.

`identity`: identity is a parameter of type `AppIdentity` that describe your application (name, stack, stage, awsRegion). See above paragraph about AppIdentity.whoAmI

`credentials`: These are the AWS credentials that will be used to load your configuration from S3. The default behaviour should be enough, but if you wanted to customise the way the credentials are picked you could pass it as a parameter. Note that it's a pass-by-name parameter so the content won't be evaluated unless needed. The default behaviour when running locally is to load the configuration from a local file, so credentials won't be evaluated in that case.

`locationFunction`: This function is a way to customise where to load the configuration depending on the environment. For instance if your configuration is in the same place for two different stacks, you could override the path that will be used. It's a partial function, so it's thought to be used as pattern matching on the `AppIdentity` you provided. You can see an example below.

##Examples

**provide your own credentials**
```scala
val config = ConfigurationLoader.load(identity, myOwnCredentials)()
```

**custom location**
```scala
val config = ConfigurationLoader.load(identity) {
  case AppIdentity(app, "stack1", stage, _) => S3ConfigurationLocation("mybucket", s"somepath/$stage/$app.conf")
  case AppIdentity(_, _, "DEV", _) => ResourceConfigurationLocation("localdev.conf")
}
```

**Play application with Compile time Dependency Injection**
```scala
import play.api.Configuration
import com.gu.AppIdentity
import com.gu.conf.ConfigurationLoader


class MyApplicationLoader extends ApplicationLoader {
  override def load(context: Context): Application = {
    LoggerConfigurator(context.environment.classLoader) foreach { _.configure(context.environment) }
    val identity = AppIdentity.whoAmI(defaultAppName = "myApp", defaultStackName = "myStack")
    val loadedConfig = Configuration(ConfigurationLoader.load(identity)())
    val newContext = context.copy(initialConfiguration = context.initialConfiguration ++ loadedConfig)
    (new BuiltInComponentsFromContext(newContext) with AppComponents).application
  }
}
```

##Location types

When providing your own mapping between `AppIdentity` and location, you can specify three location types:

- `S3ConfigurationLocation(bucket: String, path: String)`
- `FileConfigurationLocation(file: File)`
- `ResourceConfigurationLocation(resourceName: String)`

### S3ConfigurationLocation
This will help `ConfigurationLoader.load` locate the file on an S3 bucket. You must provide the bucket name and the complete path to the file.

###FileConfigurationLocation
This will be useful when loading a file ouside of your classpath. Typically, a configuration that can contain secrets and that shouldn't be committed on the repository. This is used by default when in DEV mode and points to `~/.gu/${identity.app}.conf`

###ResourceConfigurationLocation
This will load a configuration file from within your classpath. Typically a file under the `resource` folder of your project. It is useful if your configuration can be committed in your repo and is directly accessible from the classpath. 

##IAM permissions
- if you use `AppIdentity.whoAmI`
```json
{
    "Effect": "Allow",
    "Action": "ec2:DescribeTags",
    "Resource": "*"
}
```
- for `ConfigurationLoader.load`
```json
{
    "Effect": "Allow",
    "Action": "s3:GetObject",
    "Resource": "arn:aws:s3:::mybucket/*"
}
```