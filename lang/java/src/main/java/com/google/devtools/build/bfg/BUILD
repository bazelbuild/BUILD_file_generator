licenses(["notice"])  # Apache 2.0

package(default_visibility = [
    "//lang/java/src/main/java/com/google/devtools/build/bfg:__subpackages__",
    "//lang/java/src/test/java/com/google/devtools/build/bfg:__subpackages__",
])

java_library(
    name = "ReferencedClassesParser",
    srcs = ["ReferencedClassesParser.java"],
    deps = [
        "//thirdparty/jvm/com/google/auto/value:auto_value",
        "//thirdparty/jvm/com/google/code/findbugs:jsr305",
        "//thirdparty/jvm/com/google/errorprone:error_prone_annotations",
        "//thirdparty/jvm/com/google/guava",
        "//thirdparty/jvm/org/eclipse/jdt:org_eclipse_jdt_core",
    ],
)

java_library(
    name = "JavaSourceFileParser",
    srcs = [
        "JavaSourceFileParser.java",
        "JavaSourceFileParserCli.java",
    ],
    deps = [
        ":ReferencedClassesParser",
        "//src/main/java/com/google/devtools/build/bfg:bfg_java_proto",
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
