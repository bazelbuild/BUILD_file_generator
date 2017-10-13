#!/bin/bash
#
# Wrapper script around bazel-deps to generate third_party files, and check if they differ from what's at HEAD.  Exits 0
# if they match, non-zero otherwise.
# TODO(https://github.com/johnynek/bazel-deps/issues/85): Drop once issue is fixed.

set -eu

SCRIPT_DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

hash_of_third_party_files() {
    find thirdparty -type f | sort | xargs sha1sum
}

BEFORE=$(hash_of_third_party_files)

# shellcheck disable=SC1090
"$SCRIPT_DIR/generate.sh"

AFTER=$(hash_of_third_party_files)

# Fail if the generated files don't match what's committed.
if ! diff -u <(echo "$BEFORE") <(echo "$AFTER")
then
    echo
    echo "* * *"
    echo "Generated dependency files are not up to date.  Run \`dev-scripts/dependencies/generate.sh\` and commit the changes."
    echo "* * *"
    echo
    exit 2
else
    echo
    echo "Generated dependency files are up to date."
    echo
fi
