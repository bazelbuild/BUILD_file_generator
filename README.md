# BUILD File Generator

BUILD File Generator generates Bazel BUILD files for Java code.

1. It reads all `.java` files, and extracts the class dependency graph.
2. Computes the strongly connected components of the graph.
3. For each component, creates a `java_library` rule.

## Usage

TODO
