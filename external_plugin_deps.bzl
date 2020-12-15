load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "amqp_client",
        artifact = "com.rabbitmq:amqp-client:5.10.0",
        sha1 = "4de351467a13b8ca4eb7e8023032f9f964a21796",
    )
