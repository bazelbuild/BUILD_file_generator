# Language Specific Parsers

This directory contains a set of language-specific parsers, that can be used to generate a class and file level dependency graph of your project. It outputs this dependency graph as a protobuff. For users of bfg, this dependency graph can be used to generate your BUILD files.

We currently only support Java. However, we plan to add Scala support, and welcome contributions for other languages.

### Usage

```bash

bazel run //lang:JavaSourceFileParserCli 

```

TODO(bazel-devel): what do the command line options mean??? There is a TODO in the Cli asking how the files will be obtained.

