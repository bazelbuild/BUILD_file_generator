# Language Specific Parsers

This directory contains a set of language-specific parsers that are used to provide BFG with dependency information. Parsers are expected to print to stdout a serialized [ParserOutput] (https://github.com/bazelbuild/BUILD_file_generator/blob/672c5572499e96f6a89bfaa5d7baaf92184c6d7c/src/main/java/com/google/devtools/build/bfg/bfg.proto#L9) proto.
