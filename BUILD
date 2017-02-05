load("//tools/bzl:plugin.bzl", "gerrit_plugin")

gerrit_plugin(
    name = "rabbitmq",
    srcs = glob(["src/main/java/**/*.java"]),
    resources = glob(["src/main/resources/**/*"]),
    manifest_entries = [
        "Gerrit-PluginName: rabbitmq",
        "Gerrit-Module: com.googlesource.gerrit.plugins.rabbitmq.Module",
        "Implementation-Title: Gerrit rabbitmq plugin",
        "Implementation-URL: https://github.com/rinrinne/gerrit-rabbitmq-plugin",
        "Implementation-Vendor: rinrinne",
    ],
    deps = [
        "//plugins/rabbitmq/lib:amqp-client",
        "@commons_io//jar:neverlink",
        "//plugins/rabbitmq/lib:guice-multibindings",
        "@gson//jar:neverlink",
        "@commons_codec//jar:neverlink",
        "@commons_lang//jar:neverlink",
    ],
)
