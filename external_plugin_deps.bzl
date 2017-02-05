load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
  maven_jar(
    name = 'amqp-client',
    artifact = 'com.rabbitmq:amqp-client:3.5.2',
    sha1 = '8d10edd29e08f78349bd1da9d18f81c9f8b90567',
  )

  maven_jar(
    name = 'guice-multibindings',
    artifact = 'com.google.inject.extensions:guice-multibindings:4.0',
    sha1 = 'f4509545b4470bbcc865aa500ad6fef2e97d28bf',
    exclude = [
      'META-INF/DEPENDENCIES',
      'META-INF/LICENSE',
      'META-INF/NOTICE',
      'META-INF/maven/com.google.guava/guava/pom.properties',
      'META-INF/maven/com.google.guava/guava/pom.xml',
    ],
  )

