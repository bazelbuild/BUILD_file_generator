[![Build Status](https://ci.bazel.io/buildStatus/icon?job=BUILD_file_generator)](https://ci.bazel.io/job/BUILD_file_generator)

# BUILD File Generator

BUILD File Generator generates Bazel BUILD files for Java code.

1. It reads all `.java` files, and extracts the class dependency graph.
2. Computes the strongly connected components of the graph.
3. For each component, creates a `java_library` rule.

### Why is it useful?

Having all sources in a single BUILD rule doesn't allow Bazel to parallelize and
cache builds. In order to fully benefit from Bazel, one must write multiple
BUILD rules and connect them.

This project automates writing granular BUILD rules that allow Bazel to
parallelize and cache builds.

It's useful to quickly try out Bazel on your project as well as to periodically
optimize your build graph.

BFG is composed of two general components

1. Language specific parsers
2. The BFG binary

The parsers read your source code and generate a class dependency graph in the
form of a protobuf. To generate your BUILD files, you pass the generated
protobuf into the BFG binary.

### Step 1: Using parsers to generate dependency graphs

Suppose your project's Java code is in `core/src/main/java/` and
`core/src/test/java/`.

```bash
bazel run //lang/java/src/main/java/com/google/devtools/build/bfg:JavaSourceFileParserCli -- --roots=core/src/main/java,core/src/test/java $(find core/src/main/java/ core/src/test/java/ -name \*.java) > bfg.bin
```

The output is a serialized [ParserOutput] (https://github.com/bazelbuild/BUILD_file_generator/blob/672c5572499e96f6a89bfaa5d7baaf92184c6d7c/src/main/java/com/google/devtools/build/bfg/bfg.proto#L9) proto

### Step 2: Generating BUILD files using BFG binary

TODO(bazel-devel): add explanation and valid example arguments.

```bash
bazel run //src/main/java/com/google/devtools/build/bfg -- --buildozer=$BUILDOZER --whitelist=$YOUR_JAVA_PACKAGE < bfg.bin
```

### Supported Languages

We currently support Java projects. The next language on our roadmap is Scala.

## Development

## Third-party Maven dependencies

We use a [bazel-deps](https://github.com/johnynek/bazel-deps)
to manage Maven jar dependencies. All of our dependencies are listed in
[`maven_deps.yaml`](maven_deps.yaml). `bazel-deps` provides tools to manage
dependencies in that file and generates the Bazel build files for them in
`thirdparty/jvm/`.

To use `bazel-deps`, use the wrapper scripts in `dev-scripts/dependencies/`.
Don't edit the files under `thirdparty/jvm/` by hand.

To add or update a dependency, run
`./dev-scripts/dependencies/add-dep.sh MAVEN_COORD`, where `MAVEN_COORD` is the
Maven coordinate of the dependency, such as `com.google.guava:guava:23.0`.
Add the `--scala` option if it is a Scala dependency.

After running this, you'll see changes to `maven_deps.yaml` and one or more
files under `thirdparty/jvm`. Add and commit all of those changes. Similarly,
if you run `add-dep.sh` with a new version of an existing dependency, it will be
updated in `maven_deps.yaml` and any changed indirect dependencies will be
reflected in the generated files.

You can also edit `maven_deps.yaml` manually. You will need to do this to
remove a dependency, or to add exclusions to a dependency's dependencies. After
making changes, run `./dev-scripts/dependencies/generate.sh` to rebuild the
generated files, and commit the changes to the generated files.
