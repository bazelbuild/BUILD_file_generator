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
import static junit.framework.TestCase.fail;

import com.google.common.collect.ImmutableMap;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import com.google.devtools.build.bfg.GraphProcessor.GraphProcessorException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ClassToSourceGraphConsolidator}. */
@RunWith(JUnit4.class)
public class ClassToSourceGraphConsolidatorTest {

  /**
   * Example 1. Class Graph with a one to one mapping between class files and source files.
   *
   * <p>Here each class in the class graph is uniquely mapped to a source file. No source file
   * contains more than one class file.
   */
  @Test
  public void classGraphWithOneToOneMapping() {
    MutableGraph<String> graph = newGraph(String.class);
    graph.putEdge("com.A", "com.B");
    graph.putEdge("com.B", "com.C");
    graph.putEdge("com.C", "com.A");

    Map<String, Path> map =
        ImmutableMap.of(
            "com.A", Paths.get("A.java"),
            "com.B", Paths.get("B.java"),
            "com.C", Paths.get("C.java"));

    Graph<Path> actual = ClassToSourceGraphConsolidator.map(graph, map);

    MutableGraph<Path> expected = newGraph(Path.class);
    expected.putEdge(Paths.get("A.java"), Paths.get("B.java"));
    expected.putEdge(Paths.get("B.java"), Paths.get("C.java"));
    expected.putEdge(Paths.get("C.java"), Paths.get("A.java"));

    assertEquivalent(actual, expected);
  }

  /**
   * Example 2. Class Graph with a many to one mapping between class files and source files.
   *
   * <p>In this example, com.C is written within A.java rather than C.java. Since com.B depends on
   * com.C, then B.java must depend on A.java.
   */
  @Test
  public void classGraphWithManyToOneMapping() {
    MutableGraph<String> graph = newGraph(String.class);
    graph.putEdge("com.A", "com.E");
    graph.putEdge("com.B", "com.C");
    graph.putEdge("com.C", "com.D");
    graph.putEdge("com.D", "com.E");
    graph.putEdge("com.E", "com.A");

    Map<String, Path> map =
        ImmutableMap.of(
            "com.A", Paths.get("A.java"),
            "com.B", Paths.get("B.java"),
            "com.C", Paths.get("A.java"),
            "com.D", Paths.get("D.java"),
            "com.E", Paths.get("E.java"));

    Graph<Path> actual = ClassToSourceGraphConsolidator.map(graph, map);

    MutableGraph<Path> expected = newGraph(Path.class);
    expected.putEdge(Paths.get("A.java"), Paths.get("E.java"));
    expected.putEdge(Paths.get("B.java"), Paths.get("A.java"));
    expected.putEdge(Paths.get("A.java"), Paths.get("D.java"));
    expected.putEdge(Paths.get("D.java"), Paths.get("E.java"));
    expected.putEdge(Paths.get("E.java"), Paths.get("A.java"));

    assertEquivalent(actual, expected);
  }

  /**
   * Example 3. Class Graph with a many to one mapping between class files and source files and also
   * a self loop, i.e. a dependency between classes in the same file.
   *
   * <p>In this example, com.C and com.A are written in the same source file, A.java, and they
   * depend on each other. The source graph should ignore the edge between com.C and com.A.
   */
  @Test
  public void classGraphWithSelfLoops() {
    MutableGraph<String> graph = newGraph(String.class);
    graph.putEdge("com.A", "com.C");
    graph.putEdge("com.B", "com.A");
    graph.putEdge("com.C", "com.A");
    graph.putEdge("com.A", "com.B");

    Map<String, Path> map =
        ImmutableMap.of(
            "com.A", Paths.get("A.java"),
            "com.B", Paths.get("B.java"),
            "com.C", Paths.get("A.java"));

    Graph<Path> actual = ClassToSourceGraphConsolidator.map(graph, map);

    MutableGraph<Path> expected = newGraph(Path.class);
    expected.putEdge(Paths.get("A.java"), Paths.get("B.java"));
    expected.putEdge(Paths.get("B.java"), Paths.get("A.java"));

    assertEquivalent(actual, expected);
  }

  @Test
  public void classGraphWithSelfLoopsResultingInSingleSourceFile() {
    MutableGraph<String> graph = newGraph(String.class);
    graph.putEdge("com.A", "com.B");
    graph.putEdge("com.B", "com.A");

    Map<String, Path> map =
        ImmutableMap.of(
            "com.A", Paths.get("A.java"),
            "com.B", Paths.get("A.java"));

    Graph<Path> actual = ClassToSourceGraphConsolidator.map(graph, map);
    MutableGraph<Path> expected = newGraph(Path.class);
    expected.addNode(Paths.get("A.java"));

    assertEquivalent(actual, expected);
  }

  @Test
  public void classGraphWithOnlyOneClassResultingInSingleSourceFile() {
    MutableGraph<String> graph = newGraph(String.class);
    graph.addNode("com.A");

    Map<String, Path> map = ImmutableMap.of("com.A", Paths.get("A.java"));

    Graph<Path> actual = ClassToSourceGraphConsolidator.map(graph, map);
    MutableGraph<Path> expected = newGraph(Path.class);
    expected.addNode(Paths.get("A.java"));

    assertEquivalent(actual, expected);
  }

  @Test
  public void classGraphWithInnerClasses_throwsException() {
    MutableGraph<String> graph = newGraph(String.class);
    graph.putEdge("com.A$inner", "com.E");
    Map<String, Path> map =
        ImmutableMap.of("com.A", Paths.get("A.java"), "com.E", Paths.get("E.java"));
    try {
      ClassToSourceGraphConsolidator.map(graph, map);
      fail("Expected GraphProcessorException but nothing was thrown.");
    } catch (GraphProcessorException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo("Found inner class com.A$inner when mapping classes to source files");
    }
  }

  private <T> MutableGraph<T> newGraph(Class<T> unusedClazz) {
    return GraphBuilder.directed().allowsSelfLoops(false).build();
  }

  /**
   * The test graph is considered equal to control graph iff the two graphs have matching edge lists
   * and node lists.
   */
  private void assertEquivalent(Graph<Path> actual, Graph<Path> expected) {
    assertThat(actual.edges()).containsExactlyElementsIn(expected.edges());
    assertThat(actual.nodes()).containsExactlyElementsIn(expected.nodes());
  }
}
