[![Build Status](https://ci.bazel.io/buildStatus/icon?job=BUILD_file_generator)](https://ci.bazel.io/job/BUILD_file_generator)

# BUILD File Generator

BUILD File Generator generates Bazel BUILD files for Java code.

1. It reads all `.java` files, and extracts the class dependency graph.
2. Computes the strongly connected components of the graph.
3. For each component, creates a `java_library` rule.

## Usage

TODO

## Adding or updating third-party dependencies

We use a third-party tool called [bazel-deps](https://github.com/johnynek/bazel-deps)
to manage our dependencies. All of our dependencies are listed in
[`maven_deps.yaml`](maven_deps.yaml). bazel-deps provides tools to manage
adding and updating dependencies in that file, and generates the Bazel build
files for them in `thirdparty/jvm/`.

To use bazel-deps, use the wrapper scripts in `dev-scripts/dependencies/`. Don't
edit the files under `thirdparty/jvm/` by hand.

To add or update a dependency, run `./dev-scripts/dependencies/add-dep.sh MAVEN_COORD`,
where `MAVEN_COORD` is the Maven coordinate of the dependency, such as `com.google.guava:guava:23.0`.
Add the `--scala` option if it is a Scala dependency.

After running this, you'll see changes to `maven_deps.yaml` and one or more files
under `thirdparty/jvm`. Add and commit all of those changes. Similarly, if you
run `add-dep.sh` with a new version of an existing dependency, it will be updated
in `maven_deps.yaml` and any changed indirect dependencies will be reflected in
the generated files.

You can also edit `maven_deps.yaml` manually. You will need to do this to
remove a dependency, or to add exclusions to a dependency's dependencies. After
making changes, run `./dev-scripts/dependencies/generate.sh` to rebuild the
generated files, and commit the changes to the generated files.
