// Copyright 2017 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.bfg;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Streams.stream;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.ImmutableGraph;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import protos.com.google.devtools.build.bfg.Bfg;

/**
 * Entry point to BFG java source file parser. Given a list of source files, it prints a graph viz
 * dot file representing the class level dependencies between these source files.
 */
public class JavaSourceFileParserCli {

  @Option(
    name = "--roots",
    usage =
        "Comma-separated list of paths where the source/test files reside, "
            + "relative to the WORKSPACE file."
  )
  private String contentRootPaths = "src/main/java/,src/test/java/";

  @Option(
    name = "--one_rule_per_package_roots",
    usage =
        "BUILD File Generator creates one Bazel rule per file, by default. "
            + "All classes that are defined in 'oneRulePerPackageRoots' directories, "
            + "however, will be put in a single rule per Bazel package"
  )
  private String oneRulePerPackageRoots = "src/main/java";

  @Argument(usage = "Java files from which to construct a dependency graph", required = true)
  private List<String> sourceFiles = new ArrayList<>();

  public static void main(String[] args) throws Exception {
    new JavaSourceFileParserCli().run(args);
  }

  private void run(String[] args) throws Exception {
    // TODO(bazel-team) how will I receive the source files from the user.
    try {
      CmdLineParser cmdLineParser = new CmdLineParser(this);
      cmdLineParser.parseArgument(args);
    } catch (CmdLineException e) {
      if (sourceFiles.isEmpty()) {
        System.err.println("Must provide file names to parse.");
      } else {
        System.err.println(e.getMessage());
      }
      e.getParser().printUsage(System.err);
      System.exit(1);
    }

    ImmutableList<Path> contentRoots =
        stream(Splitter.on(',').split(contentRootPaths))
            .map(root -> Paths.get(root))
            .collect(toImmutableList());

    ImmutableList<Path> sourceFilePaths =
        sourceFiles.stream().map(p -> Paths.get(p)).collect(toImmutableList());

    ImmutableSet<Path> oneRulePerPackagePaths =
        stream(Splitter.on(',').split(oneRulePerPackageRoots))
            .map(root -> Paths.get(root))
            .collect(toImmutableSet());

    JavaSourceFileParser parser =
        new JavaSourceFileParser(sourceFilePaths, contentRoots, oneRulePerPackagePaths);

    ImmutableGraph<String> classDepsGraph = parser.getClassDependencyGraph();

    Set<String> unresolvedClassNames = parser.getUnresolvedClassNames();
    if (!unresolvedClassNames.isEmpty()) {
      Logger logger = Logger.getLogger(JavaSourceFileParserCli.class.getName());
      logger.warning(
          String.format("Class Names not found %s", Joiner.on("\n\t").join(unresolvedClassNames)));
    }
    Bfg.ParserOutput.Builder result = Bfg.ParserOutput.newBuilder();
    result.putAllClassToClass(writeGraphToProtoMap(classDepsGraph));
    result.build().writeTo(System.out);
  }

  private static HashMap<String, Bfg.Strings> writeGraphToProtoMap(
      ImmutableGraph<String> graph) {
    HashMap<String, Bfg.Strings> result = new HashMap<>();
    for (String u : graph.nodes()) {
      Bfg.Strings.Builder adj = Bfg.Strings.newBuilder();
      adj.addAllS(graph.adjacentNodes(u));
      result.put(u, adj.build());
    }
    return result;
  }
}
