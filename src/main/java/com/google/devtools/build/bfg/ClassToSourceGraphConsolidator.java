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

import static com.google.devtools.build.bfg.ClassNameUtilities.isInnerClass;

import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import com.google.devtools.build.bfg.GraphProcessor.GraphProcessorException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Utility class to convert a directed graph of class names to a directed graph of source files.
 *
 * <p>For example, say we had the following mapping
 *
 * <pre>
 *   digraph {
 *     com.A       -> com.C
 *     com.A$inner -> com.B
 *     com.B       -> com.C
 *     com.C       -> com.A
 *   }
 *
 *   classToSourceFileMap {
 *     com.A       -> com/A.java
 *     com.B       -> com/B.java
 *     com.C       -> com/C.java
 *   }
 * </pre>
 *
 * Then it would output the following classGraph
 *
 * <pre>
 *   digraph {
 *     com/A.java -> com/C.java
 *     com/A.java -> com/B.java
 *     com/B.java -> com/C.java
 *     com/C.java -> com/A.java
 *   }
 * </pre>
 */
public class ClassToSourceGraphConsolidator {

  /**
   * Converts the class file dependency graph into the source file dependency graph, by
   * consolidating classes from the same source file. Given as input:
   *
   * <p>ul>
   * <li>a directed graph where the nodes are classes (both inner and outer) and edges are
   *     dependencies between said classes
   * <li>a mapping between outer class files and source files.
   * </ul>
   *
   * This function outputs a directed graph where the nodes are source files and the edges are
   * dependencies between said source files.
   */
  // TODO(bazel-team): Migrate this function into Guava graph library
  public static ImmutableGraph<Path> map(
      Graph<String> classGraph, Map<String, Path> classToSourceFileMap) {
    MutableGraph<Path> graph = GraphBuilder.directed().allowsSelfLoops(false).build();
    for (String sourceNode : classGraph.nodes()) {
      if (isInnerClass(sourceNode)) {
        throw new GraphProcessorException(
            String.format("Found inner class %s when mapping classes to source files", sourceNode));
      }
      Path sourcePath = classToSourceFileMap.get(sourceNode);
      graph.addNode(sourcePath);
      for (String successorNode : classGraph.successors(sourceNode)) {
        Path successorPath = classToSourceFileMap.get(successorNode);
        if (!sourcePath.equals(successorPath)) {
          graph.putEdge(sourcePath, successorPath);
        }
      }
    }
    return ImmutableGraph.copyOf(graph);
  }
}
