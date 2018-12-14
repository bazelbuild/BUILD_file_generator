load("//thirdparty:workspace.bzl", "maven_dependencies")
load("//tools/bazel_defs:declare_maven.bzl", "declare_maven")
maven_dependencies(declare_maven)

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
# Provide dependencies for proto_library and java_proto_library rules.
http_archive(
    name = "com_google_protobuf",
    sha256 = "2244b0308846bb22b4ff0bcc675e99290ff9f1115553ae9671eba1030af31bc0",
    strip_prefix = "protobuf-3.6.1.2",
    urls = ["https://github.com/protocolbuffers/protobuf/archive/v3.6.1.2.tar.gz"],
)

rules_scala_version = "5874a2441596fe9a0bf80e167a4d7edd945c221e"  # update this as needed

http_archive(
    name = "io_bazel_rules_scala",
    strip_prefix = "rules_scala-%s" % rules_scala_version,
    type = "zip",
    url = "https://github.com/bazelbuild/rules_scala/archive/%s.zip" % rules_scala_version,
)

load("@io_bazel_rules_scala//scala:scala.bzl", "scala_repositories")
scala_repositories()

load("@io_bazel_rules_scala//scala_proto:scala_proto.bzl", "scala_proto_repositories")
scala_proto_repositories()

load("@io_bazel_rules_scala//scala:toolchains.bzl", "scala_register_toolchains")
scala_register_toolchains()
