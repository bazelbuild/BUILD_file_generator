java_test(
    name = "ReferencedClassesParserTest",
    size = "small",
    srcs = ["ReferencedClassesParserTest.java"],
    test_class = "com.google.devtools.build.bfg.ReferencedClassesParserTest",
    deps = [
        "//lang/java/src/main/java/com/google/devtools/build/bfg:ReferencedClassesParser",
        "//thirdparty/jvm/com/google/guava",
        "//thirdparty/jvm/com/google/jimfs",
        "//thirdparty/jvm/com/google/truth",
        "//thirdparty/jvm/junit",
        "//thirdparty/jvm/org/eclipse/jdt:org_eclipse_jdt_core",
    ],
)

java_test(
    name = "JavaSourceFileParserTest",
    srcs = ["JavaSourceFileParserTest.java"],
    test_class = "com.google.devtools.build.bfg.JavaSourceFileParserTest",
    deps = [
        "//lang/java/src/main/java/com/google/devtools/build/bfg:JavaSourceFileParser",
        "//lang/java/src/main/java/com/google/devtools/build/bfg:ReferencedClassesParser",
        "//thirdparty/jvm/com/google/guava",
        "//thirdparty/jvm/com/google/jimfs",
        "//thirdparty/jvm/com/google/truth",
        "//thirdparty/jvm/junit",
    ],
)