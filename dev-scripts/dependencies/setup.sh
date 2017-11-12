#!/bin/bash

set -euo pipefail

SCRIPT_DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
ROOT_DIR=$(cd "${SCRIPT_DIR}/../.." && pwd)
BAZEL_DEPS_DIR="$ROOT_DIR/../bazel-deps"
# Pin a specific version of bazel-deps; change this to upgrade:
BAZEL_DEPS_VERSION="aad33e40cd2ce2400347e5db28b767e5d512a4fd"

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

# TODO(https://github.com/bazelbuild/bazel/issues/3895): Drop once issue is fixed.
# Install buildozer and buildifier if not installed
for tool in buildozer buildifier; do
    if ! command -v "${tool}" >/dev/null 2>&1; then
        echo >&2 "${tool} is missing; attempting to install via go get."
        command -v go > /dev/null 2>&1 || { echo >&2 "go is not installed. please install go and retry."; exit 1; }
        go get github.com/bazelbuild/buildtools/"${tool}";
    fi
done

generate_and_format() {
    cd "${ROOT_DIR}"
    # TODO format-deps removes the copyright, otherwise this is nice for consistency
    # "$BAZEL_DEPS_DIR/gen_maven_deps.sh" format-deps --deps "$ROOT_DIR/maven_deps.yaml" --overwrite

    "$BAZEL_DEPS_DIR/gen_maven_deps.sh" generate --repo-root "$ROOT_DIR" --sha-file "thirdparty/workspace.bzl" --deps maven_deps.yaml

    # TODO(https://github.com/johnynek/bazel-deps/issues/73): Drop once issue is fixed.
    # The generated BUILD files are not well-formatted. Run the buildifier on them.
    find "${ROOT_DIR}/thirdparty/jvm" -name "BUILD" -exec "buildifier" -showlog -mode=fix {} +
}
