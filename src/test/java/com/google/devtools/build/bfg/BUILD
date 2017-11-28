licenses(["notice"])  # Apache 2.0

java_test(
    name = "GraphProcessorTest",
    srcs = ["GraphProcessorTest.java"],
    deps = [
        "//src/main/java/com/google/devtools/build/bfg:BuildRule",
        "//src/main/java/com/google/devtools/build/bfg:ClassToRuleResolver",
        "//src/main/java/com/google/devtools/build/bfg:GraphProcessor",
        "//src/main/java/com/google/devtools/build/bfg:TargetInfoUtilities",
        "//thirdparty/jvm/com/google/guava",
        "//thirdparty/jvm/com/google/truth",
        "//thirdparty/jvm/junit",
        "//thirdparty/jvm/org/mockito:mockito_all",
    ],
)

java_test(
    name = "ClassToSourceGraphConsolidatorTest",
    srcs = ["ClassToSourceGraphConsolidatorTest.java"],
    deps = [
        "//src/main/java/com/google/devtools/build/bfg:GraphProcessor",
        "//thirdparty/jvm/com/google/guava",
        "//thirdparty/jvm/com/google/truth",
        "//thirdparty/jvm/junit",
    ],
)

java_test(
    name = "BuildozerCommandCreatorTest",
    srcs = ["BuildozerCommandCreatorTest.java"],
    deps = [
        "//src/main/java/com/google/devtools/build/bfg:BuildRule",
        "//src/main/java/com/google/devtools/build/bfg:BuildozerCommandCreator",
        "//src/main/java/com/google/devtools/build/bfg:TargetInfoUtilities",
        "//src/main/java/com/google/devtools/build/bfg:bfg_java_proto",
        "//thirdparty/jvm/com/google/guava",
        "//thirdparty/jvm/com/google/truth",
        "//thirdparty/jvm/junit",
    ],
)

java_test(
    name = "ProjectBuildRuleTest",
    srcs = ["ProjectBuildRuleTest.java"],
    deps = [
        "//src/main/java/com/google/devtools/build/bfg:bfg_java_proto",
        "//src/main/java/com/google/devtools/build/bfg:BuildRule",
        "//src/main/java/com/google/devtools/build/bfg:TargetInfoUtilities",
        "//thirdparty/jvm/com/google/guava",
        "//thirdparty/jvm/com/google/truth",
        "//thirdparty/jvm/junit",
    ],
)

java_test(
    name = "UserDefinedResolverTest",
    srcs = ["UserDefinedResolverTest.java"],
    deps = [
        "//src/main/java/com/google/devtools/build/bfg:BuildRule",
        "//src/main/java/com/google/devtools/build/bfg:GraphProcessor",
        "//thirdparty/jvm/com/google/guava",
        "//thirdparty/jvm/com/google/truth",
        "//thirdparty/jvm/junit",
    ],
)

java_test(
    name = "ProjectClassToRuleResolverTest",
    srcs = ["ProjectClassToRuleResolverTest.java"],
    deps = [
        "//src/main/java/com/google/devtools/build/bfg:bfg_java_proto",
        "//src/main/java/com/google/devtools/build/bfg:BuildRule",
        "//src/main/java/com/google/devtools/build/bfg:GraphProcessor",
        "//src/main/java/com/google/devtools/build/bfg:TargetInfoUtilities",                 
        "//thirdparty/jvm/com/google/guava",
        "//thirdparty/jvm/com/google/jimfs",
        "//thirdparty/jvm/com/google/re2j",
        "//thirdparty/jvm/com/google/truth",
        "//thirdparty/jvm/junit",
    ],
)

java_test(
    name = "ClassNameUtilitiesTest",
    srcs = ["ClassNameUtilitiesTest.java"],
    deps = [
        "//src/main/java/com/google/devtools/build/bfg:GraphProcessor",
        "//thirdparty/jvm/com/google/truth",
        "//thirdparty/jvm/junit",
    ],
)

java_test(
    name = "ClassGraphPreprocessorTest",
    srcs = ["ClassGraphPreprocessorTest.java"],
    deps = [
        "//src/main/java/com/google/devtools/build/bfg:GraphProcessor",
        "//thirdparty/jvm/com/google/guava",
        "//thirdparty/jvm/com/google/re2j",
        "//thirdparty/jvm/com/google/truth",
        "//thirdparty/jvm/junit",
    ],
)

java_test(
    name = "BfgTest",
    srcs = ["BfgTest.java"],
    deps = [
        "//src/main/java/com/google/devtools/build/bfg:Bfg",
        "//thirdparty/jvm/com/google/guava",
        "//thirdparty/jvm/com/google/jimfs",
        "//thirdparty/jvm/com/google/re2j",
        "//thirdparty/jvm/com/google/truth",
        "//thirdparty/jvm/junit",
    ],
)

java_test(
    name = "ProjectBuildRuleUtilitiesTest",
    srcs = ["ProjectBuildRuleUtilitiesTest.java"],
    deps = [
        "//src/main/java/com/google/devtools/build/bfg:BuildRule",
        "//thirdparty/jvm/com/google/guava",
        "//thirdparty/jvm/com/google/truth",
        "//thirdparty/jvm/junit",
    ],
)
