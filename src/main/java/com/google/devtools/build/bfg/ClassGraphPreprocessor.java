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

import static com.google.devtools.build.bfg.ClassNameUtilities.getOuterClassName;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import com.google.re2j.Pattern;

/**
 * Given a directed graph of class names, processes the graph such that:
 *
 * <p>all inner class names are collapsed into their top level class name
 *
 * <p>black listed classes are removed
 *
 * <p>non white listed classes without a direct edge to a white listed class are removed
 */
public class ClassGraphPreprocessor {

  /**
   * Produces a class graph that only contains top level class names that either pass the white list
   * pattern or have an edge to node that passes the white list. Any class names that pass the black
   * list or do not pass our white list criteria are filtered from the graph.
   *
   * <p>In addition, all inner class names are collapsed into their top level parent class name.
   */
  static ImmutableGraph<String> preProcessClassGraph(
      ImmutableGraph<String> classGraph, Pattern whiteList, Pattern blackList) {
    return collapseInnerClasses(trimClassGraph(classGraph, whiteList, blackList));
  }

  /** Removes all outgoing edges from classes that are not white listed. */
  private static ImmutableGraph<String> trimClassGraph(
      ImmutableGraph<String> classGraph, Pattern whiteList, Pattern blackList) {
    MutableGraph<String> graph = GraphBuilder.directed().allowsSelfLoops(false).build();
    for (String src : classGraph.nodes()) {
      if (!whiteList.matcher(src).find() || blackList.matcher(src).find()) {
        continue;
      }
      graph.addNode(src);
      for (String dst : classGraph.successors(src)) {
        if (blackList.matcher(dst).find()) {
          continue;
        }
        graph.putEdge(src, dst);
      }
    }
    return ImmutableGraph.copyOf(graph);
  }

  /** Collapses inner classes into their top level parent class */
  private static ImmutableGraph<String> collapseInnerClasses(ImmutableGraph<String> classGraph) {
    MutableGraph<String> graph = GraphBuilder.directed().allowsSelfLoops(false).build();
    for (String src : classGraph.nodes()) {
      String outerSrc = getOuterClassName(src);
      graph.addNode(outerSrc);
      for (String dst : classGraph.successors(src)) {
        String outerDst = getOuterClassName(dst);
        if (outerSrc.equals(outerDst)) {
          continue;
        }
        graph.putEdge(outerSrc, outerDst);
      }
    }
    return ImmutableGraph.copyOf(graph);
  }
}
