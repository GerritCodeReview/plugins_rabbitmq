load("//tools/bzl:plugin.bzl", "gerrit_plugin")

gerrit_plugin(
    name = "rabbitmq",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: rabbitmq",
        "Gerrit-Module: com.googlesource.gerrit.plugins.rabbitmq.Module",
        "Implementation-Title: Gerrit rabbitmq plugin",
        "Implementation-URL: https://gerrit-review.googlesource.com/#/admin/projects/plugins/rabbitmq",
    ],
    resources = glob(["src/main/resources/**/*"]),
    deps = [
        "//lib:commons-io",
        "//lib:gson",
        "//lib/commons:codec",
        "//lib/commons:lang",
        "@amqp_client//jar",
    ],
)
