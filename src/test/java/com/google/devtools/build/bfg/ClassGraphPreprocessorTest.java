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

import static com.google.common.truth.Truth.assertThat;
import static com.google.devtools.build.bfg.ClassGraphPreprocessor.preProcessClassGraph;

import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import com.google.re2j.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ClassGraphPreprocessor}. */
@RunWith(JUnit4.class)
public class ClassGraphPreprocessorTest {

  /** Regular expression to match everything */
  private static final Pattern EVERYTHING = Pattern.compile(".*");

  /** Regular expression to match nothing. */
  private static final Pattern NOTHING = Pattern.compile("a^");

  /** Tests whether the black listed class names are removed from the class graph. */
  @Test
  public void trimRemovesBlackListedClasses() {
    MutableGraph<String> graph = newGraph();
    graph.putEdge("com.BlackList", "com.WhiteList");
    graph.putEdge("com.WhiteList", "com.BlackList");

    Pattern blackList = Pattern.compile("BlackList");

    Graph<String> actual =
        preProcessClassGraph(ImmutableGraph.copyOf(graph), EVERYTHING, blackList);

    MutableGraph<String> expected = newGraph();
    expected.addNode("com.WhiteList");

    assertEquivalent(actual, expected);
    assertThat(actual.nodes()).doesNotContain("com.BlackList");
  }

  /**
   * Tests whether after trimming the class graph, the direction of each edge is maintained. If in
   * the original graph a->b, then the resulting graph should only contain a->b and not b->a.
   */
  @Test
  public void trimMaintainsEdgeDirection() {
    MutableGraph<String> graph = newGraph();
    graph.putEdge("com.src", "com.dst");

    Graph<String> actual = preProcessClassGraph(ImmutableGraph.copyOf(graph), EVERYTHING, NOTHING);

    MutableGraph<String> expected = newGraph();
    expected.putEdge("com.src", "com.dst");

    assertEquivalent(actual, expected);
    assertThat(actual.edges()).doesNotContain(EndpointPair.ordered("com.dst", "com.src"));
  }

  /**
   * Asserts that the only nodes in the trimmed graph are white listed classes and classes that the
   * white listed classes are directly dependent on.
   */
  @Test
  public void trimRemovesTransitiveDependenciesToNonWhiteListedClasses() {
    MutableGraph<String> graph = newGraph();
    graph.putEdge("com.WhiteList", "com.OtherList");
    graph.putEdge("com.OtherList", "com.TransitiveDependency");

    Pattern whiteList = Pattern.compile("WhiteList");

    Graph<String> actual = preProcessClassGraph(ImmutableGraph.copyOf(graph), whiteList, NOTHING);

    MutableGraph<String> expected = newGraph();
    expected.putEdge("com.WhiteList", "com.OtherList");

    assertEquivalent(actual, expected);
    assertThat(actual.nodes()).doesNotContain("com.TransitiveDependency");
  }

  /**
   * Tests whether black listed classes names as well as their non-whitelisted dependencies are
   * removed from the class graph. In addition to not containing the black listed class, the
   * resulting graph should also not contain nonwhite listed classes only black listed classes are
   * dependent on. For example, say we were to have the following class graph
   *
   * <p>com.Whitelist --> com.Blacklist --> com.NonWhitelist
   *
   * <p>Then the resulting class graph should only contain com.Whitelist
   */
  @Test
  public void trimRemovesTransitiveDepsOfBlackListedClasses() {
    MutableGraph<String> graph = newGraph();
    graph.putEdge("com.BlackList", "com.OtherList");
    graph.putEdge("com.WhiteList", "com.BlackList");

    Pattern blackList = Pattern.compile("BlackList");
    Pattern whiteList = Pattern.compile("WhiteList");

    Graph<String> actual = preProcessClassGraph(ImmutableGraph.copyOf(graph), whiteList, blackList);

    MutableGraph<String> expected = newGraph();
    expected.addNode("com.WhiteList");

    assertEquivalent(actual, expected);
    assertThat(actual.nodes()).doesNotContain("com.BlackList");
    assertThat(actual.nodes()).doesNotContain("com.OtherList");
  }

  /** Ensures there are no inner classes outputted in preprocessed class graph */
  @Test
  public void collapseRemovesInnerClasses() {
    MutableGraph<String> graph = newGraph();
    graph.putEdge("Class", "Class$Inner");
    graph.putEdge("Class$Inner", "Class");

    Graph<String> actual = preProcessClassGraph(ImmutableGraph.copyOf(graph), EVERYTHING, NOTHING);

    assertThat(actual.nodes()).containsExactly("Class");
    assertThat(actual.edges()).isEmpty();
  }

  /**
   * Ensures the inner classes' dependencies on other classes are retained. For example, if an inner
   * class depends on a class A. Then, in the resulting graph, the outer class must depend on the
   * outer class of A.
   */
  @Test
  public void collapseMaintainsDepsOfInnerClassToOtherClasses() {
    MutableGraph<String> graph = newGraph();
    graph.putEdge("Class", "Class$Inner");
    graph.putEdge("Class$Inner", "OtherClass");

    Graph<String> actual = preProcessClassGraph(ImmutableGraph.copyOf(graph), EVERYTHING, NOTHING);

    assertThat(actual.nodes()).containsExactly("Class", "OtherClass");
    assertThat(actual.edges()).containsExactly(EndpointPair.ordered("Class", "OtherClass"));
  }

  /**
   * Ensures the dependencies of other classes on inner classes are retained. For example, if a
   * class A depends on an inner class B$Inner. Then, in the resulting graph, class A must depend on
   * class B.
   */
  @Test
  public void collapseMaintainsDepsOfOtherClassesOnInnerClasses() {
    MutableGraph<String> graph = newGraph();
    graph.putEdge("Class", "Class$Inner");
    graph.putEdge("OtherClass", "Class$Inner");

    Graph<String> actual = preProcessClassGraph(ImmutableGraph.copyOf(graph), EVERYTHING, NOTHING);

    assertThat(actual.nodes()).containsExactly("OtherClass", "Class");
    assertThat(actual.edges()).containsExactly(EndpointPair.ordered("OtherClass", "Class"));
  }

  /** Returns a Directed Mutable Graph */
  private MutableGraph<String> newGraph() {
    return GraphBuilder.directed().build();
  }

  /**
   * The test graph is considered equal to control graph iff the two graphs have matching edge lists
   * and node lists.
   */
  private void assertEquivalent(Graph<String> actual, Graph<String> expected) {
    assertThat(actual.edges()).containsExactlyElementsIn(expected.edges());
    assertThat(actual.nodes()).containsExactlyElementsIn(expected.nodes());
  }
}
