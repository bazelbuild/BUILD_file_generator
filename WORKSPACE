load("//thirdparty:workspace.bzl", "maven_dependencies")
load("//tools/bazel_defs:declare_maven.bzl", "declare_maven")
maven_dependencies(declare_maven)

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
# Provide dependencies for proto_library and java_proto_library rules.
http_archive(
    name = "com_google_protobuf",
    sha256 = "091e1aa2b64ea6d512ff9294ecc9da95132c3b961a8fb39a3bab3929e5122f50",
    strip_prefix = "protobuf-3.4.1",
    urls = ["https://github.com/google/protobuf/releases/download/v3.4.1/protobuf-java-3.4.1.zip"],
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
