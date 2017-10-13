#!/bin/bash
#
# Wrapper script around bazel-deps to generate the thirdparty/jvm files from maven_deps.yaml

set -euo pipefail

SCRIPT_DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

# shellcheck disable=SC1090
. "$SCRIPT_DIR/setup.sh"

generate_and_format
