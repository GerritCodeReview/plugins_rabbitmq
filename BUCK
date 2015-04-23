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
    ':guice-multibindings',
  ],
  provided_deps = [
    '//lib:gson',
    '//lib/commons:codec',
    '//lib/commons:io',
    '//lib/commons:lang',
  ],
)

maven_jar(
  name = 'amqp-client',
  id = 'com.rabbitmq:amqp-client:3.5.1',
  sha1 = '6086769bfb7a5054d97b855676aaa525b523f405',
  license = 'MPL1.1',
  exclude_java_sources = True,
  visibility = [],
)

maven_jar(
  name = 'guice-multibindings',
  id = 'com.google.inject.extensions:guice-multibindings:4.0-beta5',
  sha1 = 'f432356db0a167127ffe4a7921238d7205b12682',
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