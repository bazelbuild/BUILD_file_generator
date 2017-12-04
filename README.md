[![Build Status](https://ci.bazel.io/buildStatus/icon?job=BUILD_file_generator)](https://ci.bazel.io/job/BUILD_file_generator)

# BUILD File Generator (BFG)

BUILD File Generator, or BFG for short, is a tool for generating Bazel BUILD files from one's source code. Instead of manually going through your project and creating BUILD files and their appropriate build rules, you can rely on BFG to do it for you! 

An example project can be found here. TODO

### Why is it useful?

Bazel promises fast and correct builds, _especially_ for incremental builds. Rather than rebuild your entire codebase, Bazel _only_ rebuilds what is necessary, the targets that you have changed. For the remainder of your code, it relies on cached versions. 

There is one caveat to this fast incremental performance, though. It is dependent on the granularity of your targets. At one extreme, the least granular end, you can define a single glob for your entire project. This would be the quickest way to get your project using Bazel. However, it comes at the cost of eradicating any of Bazel's incremental build performance. On the other end, you can try to manually define the most granular targets possible, a single rule per file, collapsing any cyclic dependencies into the same target. This would ensure you make use of Bazel's blazingly fast incremental builds. However, it is extremely tedious, and somewhat annoying to maintain.

BFG automates this difficult tango.

## Usage

BFG is composed of two general components

1. Language specific parsers (LSP)
2. The BFG binary

The LSPs read your source code and generate a class dependency graph in the form of a protobuf. To generate your BUILD files, you pass the generated protobuf into the BFG binary.

### Step 0: Installation Instructions

To install BFG...TODO

### Step 1: Using LSPs to generate dependency graphs

TODO(bazel-devel): add explanation and valid example arguments.

```bash
bazel run //lang:JavaSourceFileParserCli <TODO>
```

This generates a protobuf at TODO.
TODO(bazel-devel): add clear protocol for how the protos are structured. Useful for other developers who may want to write LSPs.

### Step 2: Generating BUILD files using BFG binary

TODO(bazel-devel): add explanation and valid example arguments.

```bash
bazel run //src:bfg <TODO>
```

### Supported Languages

We currently support Java projects. The next language on our roadmap is Scala.

## Development

### Contributing

We welcome contributions! We generally prefer contributions that either (1) add features by extending BFG to other languages, (2) fix existing bugs, or (3) present bugs through the use of example projects.

### Dependency Management

To manage our dependencies, we use a third-party tool called [bazel-deps](https://github.com/johnynek/bazel-deps). All of our dependencies are listed in
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
