#!/bin/bash
#
# Wrapper script around bazel-deps to add/update dependencies.

set -euo pipefail

SCRIPT_DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

usage() {
    echo "USAGE: $0 [--java|--scala] MAVEN_COORD [MAVEN_COORD...]"
    echo
    echo "    Adds or updates the given (default java, or scala) libraries in maven_deps.yaml, and generates the corresponding files in thirdparty/."
    exit 127
}

if [ $# -eq 0 ]
then
    usage
fi

lang=java
coords=()
for arg in "$@"
do
    case $arg in
        --java)
            lang=java
            ;;
        --scala)
            lang=scala
            ;;
        --*)
            echo "unrecognized argument: $arg"
            usage
            ;;
        *)
            coords+=($arg)
            ;;
    esac
done

if [ ${#coords[@]} -eq 0 ]
then
    echo "at least one MAVEN_COORD is required"
    usage
fi

# shellcheck disable=SC1090
. "$SCRIPT_DIR/setup.sh"

"${BAZEL_DEPS_DIR}/gen_maven_deps.sh" add-dep --deps "${ROOT_DIR}/maven_deps.yaml" --lang "$lang" "${coords[@]}"

generate_and_format
