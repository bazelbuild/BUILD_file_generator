licenses(["notice"])  # Apache 2.0

package(default_visibility = [
    "//src/main/java/com/google/devtools/build/bfg:__subpackages__",
    "//src/test/java/com/google/devtools/build/bfg:__subpackages__",
])

java_library(
    name = "ReferencedClassesParser",
    srcs = ["ReferencedClassesParser.java"],
    visibility = [
        "//src/test/java/com/google/devtools/build/bfg:__subpackages__",
    ],
    deps = [
        "//thirdparty/jvm/com/google/auto/value:auto_value",
        "//thirdparty/jvm/com/google/code/findbugs:jsr305",
        "//thirdparty/jvm/com/google/errorprone:error_prone_annotations",
        "//thirdparty/jvm/com/google/guava",
        "//thirdparty/jvm/org/eclipse/jdt:org_eclipse_jdt_core",
    ],
)

java_library(
    name = "GraphProcessor",
    srcs = [
        "ClassGraphPreconditions.java",
        "ClassGraphPreprocessor.java",
        "ClassNameUtilities.java",
        "ClassToSourceGraphConsolidator.java",
        "GraphProcessor.java",
        "ProjectClassToRuleResolver.java",
        "UserDefinedResolver.java",
    ],
    deps = [
        ":BuildRule",
        ":ClassToRuleResolver",
        ":StronglyConnectedComponents",
        "//thirdparty/jvm/com/google/guava",
        "//thirdparty/jvm/com/google/re2j",
    ],
)

java_library(
    name = "BuildozerCommandCreator",
    srcs = ["BuildozerCommandCreator.java"],
    deps = [
        ":BuildRule",
        ":BuildozerCommand",
        "//thirdparty/jvm/com/google/guava",
    ],
)

java_library(
    name = "BuildozerCommand",
    srcs = ["BuildozerCommand.java"],
    deps = [
        "//thirdparty/jvm/com/google/guava",
    ],
)

java_library(
    name = "BuildRule",
    srcs = [
        "BuildRule.java",
        "ExternalBuildRule.java",
        "ProjectBuildRule.java",
        "ProjectBuildRuleUtilities.java",
    ],
    deps = [
        ":BuildozerCommand",
        ":StandardUnionFind",
        "//thirdparty/jvm/com/google/auto/value:auto_value",
        "//thirdparty/jvm/com/google/guava",
        "//thirdparty/jvm/com/google/re2j",
    ],
)

java_library(
    name = "Bfg",
    srcs = [
        "Bfg.java",
    ],
    deps = [
        ":BuildRule",
        ":BuildozerCommandCreator",
        ":ClassToRuleResolver",
        ":ExternalResolver",
        ":GraphProcessor",
        ":bfg_java_proto",
        "//thirdparty/jvm/args4j",
        "//thirdparty/jvm/com/google/guava",
        "//thirdparty/jvm/com/google/re2j",
    ],
)

java_binary(
    name = "bfg",
    main_class = "com.google.devtools.build.bfg.Bfg",
    stamp = 1,
    runtime_deps = [":Bfg"],
)

java_library(
    name = "JavaSourceFileParser",
    srcs = [
        "JavaSourceFileParser.java",
        "JavaSourceFileParserCli.java",
    ],
    deps = [
        ":ReferencedClassesParser",
        ":bfg_java_proto",
        "//thirdparty/jvm/args4j",
        "//thirdparty/jvm/com/google/guava",
        "//thirdparty/jvm/org/eclipse/jdt:org_eclipse_jdt_core",
    ],
)

java_binary(
    name = "JavaSourceFileParserCli",
    main_class = "com.google.devtools.build.bfg.JavaSourceFileParserCli",
    runtime_deps = [":JavaSourceFileParser"],
)

java_library(
    name = "ExternalResolver",
    srcs = ["ExternalResolver.java"],
    deps = [
        "//src/main/java/com/google/devtools/build/bfg:BuildRule",
        "//src/main/java/com/google/devtools/build/bfg:ClassToRuleResolver",
        "//thirdparty/jvm/com/google/guava",
    ],
)

java_library(
    name = "ClassToRuleResolver",
    srcs = ["ClassToRuleResolver.java"],
    deps = [
        "//src/main/java/com/google/devtools/build/bfg:BuildRule",
        "//thirdparty/jvm/com/google/guava",
    ],
)

java_library(
    name = "StronglyConnectedComponents",
    srcs = ["StronglyConnectedComponents.java"],
    deps = ["//thirdparty/jvm/com/google/guava"],
)

java_library(
    name = "StandardUnionFind",
    srcs = ["StandardUnionFind.java"],
    deps = [
        "//thirdparty/jvm/com/google/code/findbugs:jsr305",
        "//thirdparty/jvm/com/google/errorprone:error_prone_annotations",
        "//thirdparty/jvm/com/google/guava",
    ],
)

proto_library(
    name = "bfg_proto",
    srcs = ["bfg.proto"],
)

java_proto_library(
    name = "bfg_java_proto",
    deps = [":bfg_proto"],
)
