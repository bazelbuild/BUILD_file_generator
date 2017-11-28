#!/usr/bin/env bash
# Generate BUILD files for the Java files in e2e/java/helloworld/

set -euo pipefail

function join_by { local IFS="$1"; shift; echo "$*"; }

SCRIPT_DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
helloworld_dir="${SCRIPT_DIR}/e2e/java/helloworld"

function cleanup() {
  for file in "${helloworld_dir}/WORKSPACE" "${helloworld_dir}/src/main/java/com/google/devtools/build/bfg/example/helloworld/BUILD"; do
    test -f "${file}" && rm "${file}"
  done
}

java_files=($(find "${helloworld_dir}" -name *.java | xargs realpath))
java_digraph_file=$(mktemp --suffix=.ParserOutput.bin)

touch "${helloworld_dir}"/WORKSPACE
trap cleanup EXIT

# Run the language-specific parser for Java
cd "${SCRIPT_DIR}"
bazel build //lang/java/src/main/java/com/google/devtools/build/bfg:JavaSourceFileParserCli
${SCRIPT_DIR}/bazel-bin/lang/java/src/main/java/com/google/devtools/build/bfg/JavaSourceFileParserCli \
 --roots "${helloworld_dir}"/src/main/java \
 --one_rule_per_package_roots "${helloworld_dir}"/src/main/java \
  "$(join_by , ${java_files[@]})" > "${java_digraph_file}"

# Run the BFG
bazel build //src/main/java/com/google/devtools/build/bfg:bfg
cd "${helloworld_dir}"
greeting=$(${SCRIPT_DIR}/bazel-bin/src/main/java/com/google/devtools/build/bfg/bfg \
  --workspace "${helloworld_dir}" \
  --buildozer buildozer \
  --whitelist "com.google.devtools.build.bfg.example.helloworld.*" < "${java_digraph_file}")

cd "${SCRIPT_DIR}"
greeting=$(bazel run //e2e/java/helloworld/src/main/java/com/google/devtools/build/bfg/example/helloworld:HelloWorld)
test "${greeting}" == "Hello, world!"
