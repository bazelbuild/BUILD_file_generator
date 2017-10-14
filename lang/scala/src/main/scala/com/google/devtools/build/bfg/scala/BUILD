load("@io_bazel_rules_scala//scala:scala.bzl", "scala_binary", "scala_library")
load("@io_bazel_rules_scala//scala_proto:scala_proto.bzl", "scalapb_proto_library")

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
