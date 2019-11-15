#!/bin/bash
#
# Wrapper script around bazel-deps to generate third_party files, and check if they differ from what's at HEAD.  Exits 0
# if they match, non-zero otherwise.
# TODO(https://github.com/johnynek/bazel-deps/issues/62): check-deps.sh can be used in CI once fixed. Without this fix,
# the check falsely claims thirdparty/jvm/com/google/auto/value/BUILD is not up to date.

set -eu

SCRIPT_DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

# shellcheck disable=SC1090
. "$SCRIPT_DIR/setup.sh"

if ! "$BAZEL_DEPS_DIR/gen_maven_deps.sh" generate --check-only --buildifier buildifier --repo-root "${ROOT_DIR}" --sha-file thirdparty/workspace.bzl --deps maven_deps.yaml
then
    echo
    echo "* * *"
    echo "Generated dependency files are not up to date.  Run \`dev-scripts/dependencies/generate.sh\` and commit the changes."
    echo "* * *"
    echo
    exit 2
fi