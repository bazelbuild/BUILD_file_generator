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
import static com.google.devtools.build.bfg.DotFileParser.EDGE_PATTERN;
import static com.google.devtools.build.bfg.DotFileParser.getDirectedGraphFromDotFile;

import com.google.common.collect.ImmutableList;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import com.google.re2j.Matcher;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link DotFileParser}. */
@RunWith(JUnit4.class)
public class DotFileParserTest {

  /** RegEx Test 1. Tests behavior of regex with invalid input. */
  @Test
  public void regexIncorrectPattern() {
    String pattern = "digraph \"example5\" {";
    Matcher matcher = EDGE_PATTERN.matcher(pattern);
    assertThat(matcher.matches()).isFalse();
  }

  /**
   * RegEx Test 2. Tests behavior of regex on valid input. It should not include the semicolon,
   * arrow and additional quotations within the matched groups.
   */
  @Test
  public void regexBasicPattern() {
    String pattern = "\"A\" -> \"B\";";
    Matcher matcher = EDGE_PATTERN.matcher(pattern);
    assertThat(matcher.matches()).isTrue();
    assertThat(matcher.group(1)).isEqualTo("A");
    assertThat(matcher.group(2)).isEqualTo("B");
  }

  /**
   * RegEx Test 3. Tests behavior of regex on valid input with EXTRA jdeps info. It should ignore
   * the semicolon, arrow and additional quotations, but it should not ignore the additional jdeps
   * information.
   */
  @Test
  public void regexBasicPatternWithExtraInfo() {
    String pattern = "\"A\" -> \"B (not found)\";";
    Matcher matcher = EDGE_PATTERN.matcher(pattern);
    assertThat(matcher.matches()).isTrue();
    assertThat(matcher.group(1)).isEqualTo("A");
    assertThat(matcher.group(2)).isEqualTo("B (not found)");
  }

  /** RegEx Test 4. Tests inclusion of extra white space. */
  @Test
  public void regexWhiteSpace() {
    String pattern = "    \"A\"          -> \"B\";";
    Matcher matcher = EDGE_PATTERN.matcher(pattern);
    assertThat(matcher.matches()).isTrue();
    assertThat(matcher.group(1)).isEqualTo("A");
    assertThat(matcher.group(2)).isEqualTo("B");
  }

  /** RegEx Test 5. Tests behavior of regex on a single node without any dependencies. */
  @Test
  public void regexSingleNode() {
    String pattern = "\"A\";";
    Matcher matcher = EDGE_PATTERN.matcher(pattern);
    assertThat(matcher.matches()).isTrue();
    assertThat(matcher.group(1)).isEqualTo("A");
    assertThat(matcher.group(2)).isNull();
  }

  /** Example 1. Simple Graph */
  @Test
  public void basicExample() {
    MutableGraph<String> expected = newGraph();
    expected.putEdge("A", "B");
    expected.putEdge("A", "C");
    expected.putEdge("A", "D");
    expected.putEdge("A", "E");

    ImmutableList<String> dotFile =
        generateDotFile(
            " \"A\" -> \"B\";", " \"A\" -> \"C\";", " \"A\" -> \"D\";", " \"A\" -> \"E\";");
    Graph<String> actual = getDirectedGraphFromDotFile(dotFile);
    assertEquivalent(actual, expected);
  }

  /**
   * Example 2. Extra token test.
   *
   * <p>Tests whether regex ignores extra tokens like "(not found)"
   */
  @Test
  public void notFoundToken() {
    MutableGraph<String> expected = newGraph();
    expected.putEdge("A", "B");
    expected.putEdge("A", "C");
    expected.putEdge("A", "D");
    expected.putEdge("A", "E");

    ImmutableList<String> dotFile =
        generateDotFile(
            " \"A\" -> \"B (not found)\";",
            " \"A\" -> \"C\";",
            " \"A\" -> \"D\";",
            " \"A\" -> \"E\";");
    Graph<String> actual = getDirectedGraphFromDotFile(dotFile);
    assertEquivalent(actual, expected);
  }

  /**
   * Example 3. Extra token test number 2
   *
   * <p>Similar to Example 2 but with extra token being a (somethingsomething.jar)
   */
  @Test
  public void extraJarToken() {
    MutableGraph<String> expected = newGraph();
    expected.putEdge("A", "B");
    expected.putEdge("A", "C");
    expected.putEdge("A", "D");
    expected.putEdge("A", "E");

    ImmutableList<String> dotFile =
        generateDotFile(
            " \"A\" -> \"B (somethingsomething.jar)\";",
            " \"A\" -> \"C\";",
            " \"A\" -> \"D\";",
            " \"A\" -> \"E\";");
    Graph<String> actual = getDirectedGraphFromDotFile(dotFile);
    assertEquivalent(actual, expected);
  }

  /** Example 4. Tests behavior when there is a single node without an edge. */
  @Test
  public void unconnectedNode() {
    MutableGraph<String> expected = newGraph();
    expected.putEdge("A", "B");
    expected.putEdge("A", "C");
    expected.putEdge("A", "D");
    expected.addNode("F");

    ImmutableList<String> dotFile =
        generateDotFile(" \"A\" -> \"B\";", " \"A\" -> \"C\";", " \"A\" -> \"D\";", " \"F\";");
    Graph<String> actual = getDirectedGraphFromDotFile(dotFile);
    assertEquivalent(actual, expected);
  }

  /** Example 5. Tests behavior when there are multiple dot files with no repeated edges */
  @Test
  public void multipleJarsWithNoRepeatedEdgesOrNodes() {
    MutableGraph<String> expected = newGraph();
    expected.putEdge("A", "B");
    expected.putEdge("C", "F");
    expected.putEdge("A", "D");
    expected.addNode("F");

    ImmutableList.Builder<String> dotFile = ImmutableList.builder();
    dotFile.addAll(generateDotFile(" \"C\" -> \"F\";", " \"F\";"));
    dotFile.addAll(generateDotFile(" \"A\" -> \"B\";", " \"A\" -> \"D\";"));

    Graph<String> actual = getDirectedGraphFromDotFile(dotFile.build());
    assertEquivalent(actual, expected);
  }

  /**
   * Example 6. Tests behavior when there are multiple dot files with some repeated nodes. There
   * should not be duplicates in the resulting graph.
   */
  @Test
  public void multipleJarsWithDuplicateEdgesAndNodes() {
    MutableGraph<String> expected = newGraph();
    expected.putEdge("A", "B");
    expected.putEdge("A", "C");
    expected.putEdge("A", "D");
    expected.addNode("F");

    ImmutableList.Builder<String> dotFile = ImmutableList.builder();
    dotFile.addAll(
        generateDotFile(" \"A\" -> \"B\";", " \"A\" -> \"C\";", " \"A\" -> \"D\";", " \"F\";"));
    dotFile.addAll(
        generateDotFile(" \"A\" -> \"B\";", " \"A\" -> \"C\";", " \"A\" -> \"D\";", " \"F\";"));

    Graph<String> actual = getDirectedGraphFromDotFile(dotFile.build());
    assertEquivalent(actual, expected);
  }

  /**
   * Example 7. Tests behavior when there are multiple dot files with some repeated nodes. There
   * should not be duplicates in the resulting graph.
   */
  @Test
  public void multipleJarsWithRepeatedEdges() {
    MutableGraph<String> expected = newGraph();
    expected.putEdge("A", "B");
    expected.putEdge("A", "C");
    expected.putEdge("A", "D");
    expected.addNode("F");

    List<String> dotFile = new ArrayList<>();
    dotFile.addAll(generateDotFile(" \"A\" -> \"C\";", " \"A\" -> \"D\";", " \"F\";"));
    dotFile.addAll(generateDotFile(" \"A\" -> \"B\";", " \"A\" -> \"C\";", " \"F\";"));

    Graph<String> actual = getDirectedGraphFromDotFile(dotFile);
    assertEquivalent(actual, expected);
  }
  /**
   * Creates a mock DotFile of the following form.
   *
   * <pre>
   * digraph "graphName" {
   *   edges[0]
   *   edges[1]
   *   ...
   * }
   *
   * Edges are formed as follows
   *    "src_node" -> "dst_node";
   *    "src_node"
   *
   * </pre>
   */
  private ImmutableList<String> generateDotFile(String... edges) {
    ImmutableList.Builder<String> lines = ImmutableList.builder();
    lines.add("digraph graph {");
    lines.add(edges);
    lines.add("}");
    return lines.build();
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
