load("//thirdparty:workspace.bzl", "maven_dependencies")
load("//thirdparty:load.bzl", "declare_maven")

maven_dependencies(declare_maven)

# Provide dependencies for proto_library and java_proto_library rules.
http_archive(
    name = "com_google_protobuf",
    urls = ["https://github.com/google/protobuf/releases/download/v3.4.1/protobuf-java-3.4.1.zip"],
    strip_prefix = "protobuf-3.4.1",
    sha256 = "091e1aa2b64ea6d512ff9294ecc9da95132c3b961a8fb39a3bab3929e5122f50",
)
