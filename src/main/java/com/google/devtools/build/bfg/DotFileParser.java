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

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.util.List;

/**
 * Takes as input a list of strings representing lines from a <code>jdeps</code> [1] produced DOT
 * files and outputs a directed graph.
 *
 * <p><code>jdeps</code> [1] produces DOT files which contain an edge list of dependencies in the
 * following form:
 *
 * <pre>
 * digraph "name_of_jar" {
 *   "examplefile0.class" -> "parentExampleFile0.class";
 *   "examplefile1.class" -> "parentExampleFile1.class";
 *   "examplefile1.class" -> "parentExampleFile2.class (something.jar)";
 *   "independent.class";
 *
 *    ...
 * }
 * </pre>
 *
 * Each class is wrapped with quotations and each line ends with a semicolon. On occasion, each
 * class may possess extraneous output indicating the jar file it originated from. NOTE, a single
 * DOT file may contain the dependency graphs of multiple jar files.
 *
 * <p>[1] - https://wiki.openjdk.java.net/display/JDK8/Java+Dependency+Analysis+Tool
 */
class DotFileParser {

  /**
   * Pattern to extract a dependency between two classes from the dot file. The pattern must handle
   * the scenario when there is a single node without any dependencies, In addition, it must also
   * handle jdeps inconsistent whitespace formatting.
   */
  static final Pattern EDGE_PATTERN =
      Pattern.compile("^\\s*\"([^\"]+)\"(?:(?:\\s*->\\s*\")(.*)\")?;\\s*$");

  static ImmutableGraph<String> getDirectedGraphFromDotFile(List<String> lines) {
    MutableGraph<String> graph = GraphBuilder.directed().build();
    for (String line : lines) {
      Matcher edgeMatcher = EDGE_PATTERN.matcher(line);
      if (!edgeMatcher.matches()) {
        continue;
      }
      String srcNodeLabel = formatNodeLabel(edgeMatcher.group(1));
      graph.addNode(srcNodeLabel);
      if (edgeMatcher.group(2) != null) {
        String dstNodelabel = formatNodeLabel(edgeMatcher.group(2));
        graph.addNode(dstNodelabel);
        graph.putEdge(srcNodeLabel, dstNodelabel);
      }
    }
    return ImmutableGraph.copyOf(graph);
  }

  /**
   * On occasion jdeps will return extra tokens with each node such as
   *
   * <pre>
   * digraph "name_of_jar" {
   *    "examplefile0.class" -> "parentExampleFile0.class (something.jar)";
   *    "examplefile1.class" -> "parentExampleFile1.class (not found)";
   * }
   *
   * We want to filter the additional information wrapped in the parenthesis.
   *
   * </pre>
   */
  private static String formatNodeLabel(String nodeLabel) {
    return nodeLabel.split(" ")[0];
  }
}
