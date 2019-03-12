load("@io_bazel_rules_scala//scala:scala.bzl", "scala_binary", "scala_library")
load("@io_bazel_rules_scala//scala_proto:scala_proto.bzl", "scalapb_proto_library")
load("@io_bazel_rules_scala//scala_proto:scala_proto_toolchain.bzl", "scala_proto_toolchain")

scala_proto_toolchain(
    name = "scala_proto_toolchain_configuration",
    # with_java=True,
    visibility = ["//visibility:public"],
    with_flat_package = False,
    with_grpc = False,
    with_single_line_to_string = True,
)

toolchain(
    name = "scalapb_toolchain",
    toolchain = ":scala_proto_toolchain_configuration",
    toolchain_type = "@io_bazel_rules_scala//scala_proto:toolchain_type",
    visibility = ["//visibility:public"],
)


scala_library(
    name = "ScalaSourceFileParser",
    srcs = [
        "ScalaSourceFileParser.scala",
        "ScalaSourceFileParserCli.scala",
    ],
    visibility = ["//visibility:public"],
    deps = [
        ":scalapb_bfg_proto",
    ],
)

scalapb_proto_library(
    name = "scalapb_bfg_proto",
    visibility = ["//visibility:public"],
    deps = ["//src/main/java/com/google/devtools/build/bfg:bfg_proto"],
)

scala_binary(
    name = "ScalaSourceFileParserCli",
    main_class = "com.google.devtools.build.bfg.scala.ScalaSourceFileParserCli",
    runtime_deps = [":ScalaSourceFileParser"],
)
