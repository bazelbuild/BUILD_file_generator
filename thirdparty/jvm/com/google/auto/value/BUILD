java_library(
    name = "auto_value",
    exported_plugins = [
        ":auto_value_plugin",
    ],
    visibility = [
        "//visibility:public",
    ],
    exports = [
        "//external:jar/com/google/auto/value/auto_value",
    ],
)

java_plugin(
    name = "auto_value_plugin",
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    deps = [
        "//external:jar/com/google/auto/value/auto_value",
    ],
)
