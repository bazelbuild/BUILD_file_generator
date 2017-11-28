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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import com.google.devtools.build.bfg.GraphProcessor.GraphProcessorException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link GraphProcessor}. */
@RunWith(JUnit4.class)
public class GraphProcessorTest {

  private static final Path WORKSPACE_DEFAULT = Paths.get("/workspace");
  /** Tests behavior of graph processor when each class is mapped to its own build rule. */
  @Test
  public void bijectiveClassRuleMapping() {
    MutableGraph<String> classgraph = GraphBuilder.directed().allowsSelfLoops(false).build();
    classgraph.putEdge("com.A", "com.B");
    classgraph.putEdge("com.B", "com.C");

    GraphProcessor processor = new GraphProcessor(ImmutableGraph.copyOf(classgraph));

    String packagePath = "/java/com/";
    BuildRule ruleA = newBuildRule(packagePath, "/java/com/A.java");
    BuildRule ruleB = newBuildRule(packagePath, "/java/com/B.java");
    BuildRule ruleC = newBuildRule(packagePath, "/java/com/C.java");
    ClassToRuleResolver resolver = mock(ClassToRuleResolver.class);
    when(resolver.resolve(classgraph.nodes()))
        .thenReturn(ImmutableMap.of("com.A", ruleA, "com.B", ruleB, "com.C", ruleC));

    Graph<BuildRule> actual = processor.createBuildRuleDAG(ImmutableList.of(resolver));

    MutableGraph<BuildRule> expected = GraphBuilder.directed().allowsSelfLoops(false).build();
    expected.putEdge(ruleA, ruleB);
    expected.putEdge(ruleB, ruleC);

    assertThatGraphsEqual(actual, expected);
  }

  /**
   * Tests behavior when multiple classes map to the same build rule. Ensures that the resulting
   * build rule graph has no self loops.
   */
  @Test
  public void manyClassesToOneRuleNoSelfLoop() {
    MutableGraph<String> classgraph = GraphBuilder.directed().allowsSelfLoops(false).build();
    classgraph.putEdge("com.A", "com.B");
    classgraph.putEdge("com.B", "com.C");

    GraphProcessor processor = new GraphProcessor(ImmutableGraph.copyOf(classgraph));

    String packagePath = "/java/com/";
    BuildRule ruleA = newBuildRule(packagePath, "/java/com/A.java");
    BuildRule ruleC = newBuildRule(packagePath, "/java/com/C.java");
    ClassToRuleResolver resolver = mock(ClassToRuleResolver.class);
    when(resolver.resolve(classgraph.nodes()))
        .thenReturn(ImmutableMap.of("com.A", ruleA, "com.B", ruleA, "com.C", ruleC));

    Graph<BuildRule> actual = processor.createBuildRuleDAG(ImmutableList.of(resolver));
    assertThat(actual.edges()).doesNotContain(EndpointPair.ordered(ruleA, ruleA));

    MutableGraph<BuildRule> expected = GraphBuilder.directed().allowsSelfLoops(false).build();
    expected.putEdge(ruleA, ruleC);

    assertThatGraphsEqual(actual, expected);
  }

  /**
   * Tests behavior when a class is mapped to multiple build rules. The Graph Processor should
   * ignore the mapping of the secondary resolver. This also tests whether the resolvers are called
   * in an appropriate fashion. The first resolver should be called on the entire class graph. The
   * second resolver should be called on an empty set. If it is called on any other argument, then
   * this test will throw either a GraphProcessorException or a NullPointerException.
   */
  @Test
  public void multipleResolversWithDifferingResolutions() {
    MutableGraph<String> classgraph = GraphBuilder.directed().allowsSelfLoops(false).build();
    classgraph.putEdge("com.A", "com.B");

    GraphProcessor processor = new GraphProcessor(ImmutableGraph.copyOf(classgraph));

    String packagePath = "/java/com/";
    BuildRule ruleA = newBuildRule(packagePath, "/java/com/A.java");
    BuildRule ruleB = newBuildRule(packagePath, "/java/com/B.java");
    ClassToRuleResolver resolver1 = mock(ClassToRuleResolver.class);
    when(resolver1.resolve(classgraph.nodes()))
        .thenReturn(ImmutableMap.of("com.A", ruleA, "com.B", ruleB));

    ClassToRuleResolver resolver2 = mock(ClassToRuleResolver.class);
    when(resolver2.resolve(classgraph.nodes())).thenReturn(ImmutableMap.of("com.B", ruleA));
    when(resolver2.resolve(ImmutableSet.of())).thenReturn(ImmutableMap.of());

    try {
      Graph<BuildRule> actual =
          processor.createBuildRuleDAG(ImmutableList.of(resolver1, resolver2));
      MutableGraph<BuildRule> expected = GraphBuilder.directed().allowsSelfLoops(false).build();
      expected.putEdge(ruleA, ruleB);
      assertThatGraphsEqual(actual, expected);
    } catch (GraphProcessorException e) {
      fail(
          "Threw a run time exception when class was mapped by multiple resolvers"
              + "Class should have been ignored after first resolver.");
    }
  }

  /** Ensures that given an inner class, the graph processor throws an illegal state exception. */
  @Test
  public void doesNotSupportInnerClassNames() {
    MutableGraph<String> classgraph = GraphBuilder.directed().allowsSelfLoops(false).build();
    classgraph.putEdge("com.A", "com.A$InnerClass");

    try {
      new GraphProcessor(ImmutableGraph.copyOf(classgraph));
      fail("Expected an exception, but nothing was thrown.");
    } catch (IllegalStateException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              "Argument contained inner class name com.A$InnerClass but expected no inner classes");
    }
  }

  /**
   * Tests behavior when multiple resolvers are used and they could potentially resolve overlapping
   * classes. The second resolver if called with no argument, should error out. However, we want to
   * assert that it is instead called with an argument (a set of classes to execute on).
   */
  @Test
  public void multipleResolversWithOverlappingResolutions() {
    MutableGraph<String> classgraph = GraphBuilder.directed().allowsSelfLoops(false).build();
    classgraph.putEdge("com.A", "com.B");
    classgraph.putEdge("com.B", "com.C");

    GraphProcessor processor = new GraphProcessor(ImmutableGraph.copyOf(classgraph));
    String packagePath = "/java/com/";
    BuildRule ruleA = newBuildRule(packagePath, "/java/com/A.java");
    BuildRule ruleB = newBuildRule(packagePath, "/java/com/B.java");
    BuildRule ruleC = newBuildRule(packagePath, "/java/com/C.java");

    ClassToRuleResolver resolver = mock(ClassToRuleResolver.class);
    when(resolver.resolve(classgraph.nodes()))
        .thenReturn(ImmutableMap.of("com.A", ruleA, "com.B", ruleB));

    ClassToRuleResolver resolver2 = mock(ClassToRuleResolver.class);
    when(resolver2.resolve(classgraph.nodes()))
        .thenReturn(ImmutableMap.of("com.B", ruleB, "com.C", ruleC));
    when(resolver2.resolve(ImmutableSet.of("com.C"))).thenReturn(ImmutableMap.of("com.C", ruleC));

    try {
      Graph<BuildRule> actual = processor.createBuildRuleDAG(ImmutableList.of(resolver, resolver2));
      MutableGraph<BuildRule> expected = GraphBuilder.directed().allowsSelfLoops(false).build();
      expected.putEdge(ruleA, ruleB);
      expected.putEdge(ruleB, ruleC);
      assertThatGraphsEqual(actual, expected);
    } catch (GraphProcessorException e) {
      fail(
          "Threw a run time exception when class was mapped by multiple resolvers"
              + "Class should have been ignored after first resolver.");
    }
  }

  /**
   * Tests behavior of graph processor when no classes are mapped to build rules. The resulting DAG
   * should be empty.
   */
  @Test
  public void emptyMapping() {
    MutableGraph<String> classgraph = GraphBuilder.directed().allowsSelfLoops(false).build();
    classgraph.putEdge("com.A", "com.B");
    classgraph.putEdge("com.B", "com.C");
    classgraph.putEdge("com.C", "com.D");

    GraphProcessor processor = new GraphProcessor(ImmutableGraph.copyOf(classgraph));

    ClassToRuleResolver resolver = mock(ClassToRuleResolver.class);
    when(resolver.resolve(classgraph.nodes())).thenReturn(ImmutableMap.of());

    Graph<BuildRule> actual = processor.createBuildRuleDAG(ImmutableList.of(resolver));
    assertThat(actual.nodes()).isEmpty();
    assertThat(actual.edges()).isEmpty();
  }

  private void assertThatGraphsEqual(Graph<BuildRule> actual, Graph<BuildRule> expected) {
    assertThat(actual.nodes()).containsExactlyElementsIn(expected.nodes());
    assertThat(actual.edges()).containsExactlyElementsIn(expected.edges());
  }

  private static BuildRule newBuildRule(String packagePathString, String... srcFiles) {
    Path packagePath = WORKSPACE_DEFAULT.resolve(packagePathString);
    Path[] srcFilePaths =
        Arrays.stream(srcFiles).map(srcFile -> packagePath.resolve(srcFile)).toArray(Path[]::new);
    // For these tests we can assume these are all java_library rules.
    return new ProjectBuildRule(Maps.toMap(ImmutableSet.copyOf(srcFilePaths), p -> TargetInfoUtilities.javaLibrary()), packagePath, WORKSPACE_DEFAULT);
  }
}
