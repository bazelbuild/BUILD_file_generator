#!/bin/bash

set -euo pipefail

SCRIPT_DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
ROOT_DIR=$(cd "${SCRIPT_DIR}/../.." && pwd)
BAZEL_DEPS_DIR="$ROOT_DIR/../bazel-deps"
# Pin a specific version of bazel-deps; change this to upgrade:
BAZEL_DEPS_VERSION="4ac70de70f9ba3fd61f1d9ede7729c4fc63a31b7"

if [ -d "$BAZEL_DEPS_DIR" ]
then
    cd "$BAZEL_DEPS_DIR"
    git fetch origin master
else
    git clone https://github.com/johnynek/bazel-deps.git "$BAZEL_DEPS_DIR"
fi

cd "$BAZEL_DEPS_DIR"
git reset --hard ${BAZEL_DEPS_VERSION}

bazel build src/scala/com/github/johnynek/bazel_deps:parseproject_deploy.jar

generate_and_format() {
    cd "${ROOT_DIR}"
    # TODO format-deps removes the copyright, otherwise this is nice for consistency
    # "$BAZEL_DEPS_DIR/gen_maven_deps.sh" format-deps --deps "$ROOT_DIR/maven_deps.yaml" --overwrite

    "$BAZEL_DEPS_DIR/gen_maven_deps.sh" generate --repo-root "$ROOT_DIR" --sha-file "thirdparty/workspace.bzl" --deps maven_deps.yaml

    # TODO(https://github.com/johnynek/bazel-deps/issues/62): Drop once issue is fixed.
    # Manually add the AutoValue plugin. Otherwise, everything can be auto-generated from the YAML.
    buildozer 'add exported_plugins :auto_value_plugin' //thirdparty/jvm/com/google/auto/value:auto_value
    buildozer 'new java_plugin auto_value_plugin' //thirdparty/jvm/com/google/auto/value:auto_value
    buildozer 'set processor_class com.google.auto.value.processor.AutoValueProcessor' //thirdparty/jvm/com/google/auto/value:auto_value_plugin
    buildozer 'add deps //external:jar/com/google/auto/value/auto_value' //thirdparty/jvm/com/google/auto/value:auto_value_plugin

    # TODO(https://github.com/johnynek/bazel-deps/issues/73): Drop once issue is fixed.
    # The generated BUILD files are not well-formatted. Run the buildifier on them.
    find "${ROOT_DIR}/thirdparty/jvm" -name "BUILD" -exec "buildifier" -showlog -mode=fix {} +
}
