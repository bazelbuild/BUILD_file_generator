workspace(name = "build_file_generator")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("//thirdparty:workspace.bzl", "maven_dependencies")
maven_dependencies()

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
# Provide dependencies for proto_library and java_proto_library rules.
http_archive(
    name = "com_google_protobuf",
    sha256 = "73fdad358857e120fd0fa19e071a96e15c0f23bb25f85d3f7009abfd4f264a2a",
    strip_prefix = "protobuf-3.6.1.3",
    urls = ["https://github.com/protocolbuffers/protobuf/archive/v3.6.1.3.tar.gz"],
)

rules_scala_version = "7bc18d07001cbfd425c6761c8384c4e982d25a2b"  # master as of 2019-03-12

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

register_toolchains("//lang/scala/src/main/scala/com/google/devtools/build/bfg/scala:scalapb_toolchain")