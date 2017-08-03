load("//tools/bzl:plugin.bzl", "gerrit_plugin")

gerrit_plugin(
    name = "rabbitmq",
    srcs = glob(["src/main/java/**/*.java"]),
    resources = glob(["src/main/resources/**/*"]),
    manifest_entries = [
        "Gerrit-PluginName: rabbitmq",
        "Gerrit-Module: com.googlesource.gerrit.plugins.rabbitmq.Module",
        "Implementation-Title: Gerrit rabbitmq plugin",
        "Implementation-URL: https://gerrit-review.googlesource.com/#/admin/projects/plugins/rabbitmq",
    ],
    deps = [
        "@amqp_client//jar",
        "@commons_codec//jar:neverlink",
        "@commons_io//jar",
        "@commons_lang//jar:neverlink",
        "@gson//jar:neverlink",
    ],
)
