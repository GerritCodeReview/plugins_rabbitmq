load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "amqp_client",
        artifact = "com.rabbitmq:amqp-client:4.1.1",
        sha1 = "256f6c92c55a8d3cfae8d32e1a15713baedab184",
    )
