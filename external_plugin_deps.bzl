load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "amqp_client",
        artifact = "com.rabbitmq:amqp-client:3.5.2",
        sha1 = "8d10edd29e08f78349bd1da9d18f81c9f8b90567",
    )
