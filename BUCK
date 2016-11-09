include_defs('//bucklets/gerrit_plugin.bucklet')
include_defs('//bucklets/maven_jar.bucklet')

gerrit_plugin(
  name = 'rabbitmq',
  srcs = glob(['src/main/java/**/*.java']),
  resources = glob(['src/main/resources/**/*']),
  manifest_entries = [
    'Gerrit-PluginName: rabbitmq',
    'Gerrit-Module: com.googlesource.gerrit.plugins.rabbitmq.Module',
    'Implementation-Title: Gerrit rabbitmq plugin',
    'Implementation-URL: https://github.com/rinrinne/gerrit-rabbitmq-plugin',
    'Implementation-Vendor: rinrinne',
  ],
  deps = [
    ':amqp-client',
    ':commons-codec',
    ':commons-io',
    ':guice-multibindings',
  ],
)

java_library(
  name = 'classpath',
  deps = [':rabbitmq__plugin'],
)

maven_jar(
  name = 'commons-codec',
  id = 'commons-codec:commons-codec:1.4',
  sha1 = '4216af16d38465bbab0f3dff8efa14204f7a399a',
  license = 'Apache2.0',
  exclude = ['META-INF/LICENSE.txt', 'META-INF/NOTICE.txt'],
)

maven_jar(
  name = 'commons-io',
  id = 'commons-io:commons-io:1.4',
  sha1 = 'a8762d07e76cfde2395257a5da47ba7c1dbd3dce',
  license = 'Apache2.0',
)

maven_jar(
  name = 'amqp-client',
  id = 'com.rabbitmq:amqp-client:3.5.2',
  sha1 = '8d10edd29e08f78349bd1da9d18f81c9f8b90567',
  license = 'MPL1.1',
  exclude_java_sources = True,
  visibility = [],
)

maven_jar(
  name = 'guice-multibindings',
  id = 'com.google.inject.extensions:guice-multibindings:4.0',
  sha1 = 'f4509545b4470bbcc865aa500ad6fef2e97d28bf',
  license = 'Apache2.0',
  exclude_java_sources = True,
  exclude = [
    'META-INF/DEPENDENCIES',
    'META-INF/LICENSE',
    'META-INF/NOTICE',
    'META-INF/maven/com.google.guava/guava/pom.properties',
    'META-INF/maven/com.google.guava/guava/pom.xml',
  ],
  visibility = [],
)
