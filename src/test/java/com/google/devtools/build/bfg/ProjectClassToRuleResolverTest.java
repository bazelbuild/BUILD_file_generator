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
import static com.google.common.truth.Truth.assertThat;
import static com.google.devtools.build.bfg.ProjectClassToRuleResolver.UNRESOLVED_THRESHOLD;
import static junit.framework.TestCase.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.re2j.Pattern;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ProjectClassToRuleResolver}. */
@RunWith(JUnit4.class)
public class ProjectClassToRuleResolverTest {

  private Path workspace;

  private static final Pattern WHITELIST_DEFAULT = Pattern.compile(".*");

  @Before
  public void setUp() throws IOException {
    FileSystem fileSystem = createDefaultFileSystem();
    workspace = fileSystem.getPath("/src");
    Files.createDirectories(workspace.resolve("java/com/"));
  }

  /**
   * Tests behavior of resolver on a graph where each class is uniquely mapped to a target.
   * Resulting map should have each class uniquely mapped to a build rule.
   */
  @Test
  public void simpleDAGWithUniqueTargets() throws IOException {
    MutableGraph<String> classgraph = newGraph(String.class);
    classgraph.putEdge("com.A", "com.B");
    classgraph.putEdge("com.B", "com.C");
    writeFiles("java/com/A.java", "java/com/B.java", "java/com/C.java");

    ProjectClassToRuleResolver resolver = newResolver(classgraph, WHITELIST_DEFAULT, "java/");

    ImmutableMap<String, BuildRule> actual = resolver.resolve(classgraph.nodes());

    String packagePath = "/src/java/com/";
    BuildRule ruleA = buildRule(packagePath, "/src/java/com/A.java");
    BuildRule ruleB = buildRule(packagePath, "/src/java/com/B.java");
    BuildRule ruleC = buildRule(packagePath, "/src/java/com/C.java");

    assertThat(actual).containsExactly("com.A", ruleA, "com.B", ruleB, "com.C", ruleC);
  }

  /**
   * Tests behavior of resolver on a graph where each class is uniquely mapped to a target, AND only
   * a subset of classes have been asked to be resolved. Resulting map should have each class, in
   * the provided list, uniquely mapped to a build rule.
   */
  @Test
  public void filteredDAGWithUniqueTargets() throws IOException {
    MutableGraph<String> classgraph = newGraph(String.class);
    classgraph.putEdge("com.A", "com.B");

    writeFiles("java/com/A.java", "java/com/B.java");

    ProjectClassToRuleResolver resolver = newResolver(classgraph, WHITELIST_DEFAULT, "java/");

    ImmutableMap<String, BuildRule> actual = resolver.resolve(ImmutableSet.of("com.A"));

    String packagePath = "/src/java/com/";
    assertThat(actual).containsExactly("com.A", buildRule(packagePath, "/src/java/com/A.java"));
    assertThat(actual).doesNotContainKey("com.B");
  }

  /**
   * Tests behavior of resolver on a graph where all classes map to the same target. Resulting map
   * should have each class map to the same build rule.
   */
  @Test
  public void graphWithOnlyOneTarget() throws IOException {
    MutableGraph<String> classgraph = newGraph(String.class);
    classgraph.putEdge("com.A", "com.B");
    classgraph.putEdge("com.B", "com.C");
    classgraph.putEdge("com.C", "com.A");

    writeFiles("java/com/A.java", "java/com/B.java", "java/com/C.java");

    ProjectClassToRuleResolver resolver = newResolver(classgraph, WHITELIST_DEFAULT, "java/");

    ImmutableMap<String, BuildRule> actual = resolver.resolve(classgraph.nodes());
    assertThat(actual).containsKey("com.A");
    assertThat(actual.get("com.A")).isEqualTo(actual.get("com.B"));
    assertThat(actual.get("com.B")).isEqualTo(actual.get("com.C"));
  }

  /**
   * Tests behavior of resolver on a graph when an inner class is provided. Resolver should throw an
   * Illegal State Exception in this event.
   */
  @Test
  public void graphWithInnerClass() throws IOException {
    MutableGraph<String> classgraph = newGraph(String.class);
    classgraph.putEdge("com.A$hello", "com.B");

    try {
      newResolver(classgraph, WHITELIST_DEFAULT, "java/");
      fail("Expected an exception, but nothing was thrown.");
    } catch (IllegalStateException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              "Argument contained inner class name com.A$hello but expected no inner classes");
    }
  }

  /**
   * Tests behavior of resolver when there are no classes that match white list pattern. The
   * resulting map should contain no entries.
   */
  @Test
  public void noMatchingClassNames() throws IOException {
    MutableGraph<String> classgraph = newGraph(String.class);
    classgraph.putEdge("com.A", "com.B");
    classgraph.putEdge("com.B", "com.C");

    writeFiles("java/com/A.java", "java/com/B.java", "java/com/C.java");

    ProjectClassToRuleResolver resolver =
        newResolver(classgraph, Pattern.compile("com.hello.*"), "java/");

    ImmutableMap<String, BuildRule> actual = resolver.resolve(classgraph.nodes());
    assertThat(actual).isEmpty();
  }

  /**
   * If a class's source file cannot be found, then that class name should not be in the ensuing
   * class. Since less than the default threshold are unresolved, BFG should NOT error out.
   */
  @Test
  public void ignoresUnresolvedClass_smallerThanThreshold() throws IOException {
    MutableGraph<String> classgraph = newGraph(String.class);
    classgraph.putEdge("com.A", "com.DoesNotExist");

    writeFiles("java/com/A.java");

    ProjectClassToRuleResolver resolver = newResolver(classgraph, WHITELIST_DEFAULT, "java/");
    ImmutableMap<String, BuildRule> actual = resolver.resolve(classgraph.nodes());
    assertThat(actual)
        .containsExactly("com.A", buildRule("/src/java/com/", "/src/java/com/A.java"));
    assertThat(actual).doesNotContainKey("com.DoesNotExist");
  }

  /**
   * If a class's source file cannot be found, then that class name should not be in the ensuing
   * class. If more than the default threshold are unresolved, then it BFG should error out.
   */
  @Test
  public void ignoresUnresolvedClass_exceedsThreshold() throws IOException {
    MutableGraph<String> classgraph = newGraph(String.class);
    classgraph.putEdge("com.A", "com.DoesNotExist");
    classgraph.putEdge("com.A", "com.DoesNotExistTwo");
    classgraph.putEdge("com.A", "com.DoesNotExistThree");

    writeFiles("java/com/A.java");

    ProjectClassToRuleResolver resolver = newResolver(classgraph, WHITELIST_DEFAULT, "java/");

    try {
      resolver.resolve(classgraph.nodes());
      fail("Expected an exception, but nothing was thrown.");
    } catch (IllegalStateException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              String.format(
                  "BUILD File Generator failed to map over %.0f percent of class names. "
                      + "Check your white list and content roots",
                  UNRESOLVED_THRESHOLD * 100));
    }
  }

  /** Constructs a ProjectClassToRuleResolver using default workspace path and arguments */
  private ProjectClassToRuleResolver newResolver(
      Graph<String> classGraph, Pattern whiteList, String... roots) {
    ImmutableList<Path> rootPaths =
        Arrays.stream(roots).map(root -> workspace.resolve(root)).collect(toImmutableList());
    return new ProjectClassToRuleResolver(
        ImmutableGraph.copyOf(classGraph), whiteList, rootPaths, workspace);
  }

  /** Constructs a ProjectBuildRule using default workspace path */
  private ProjectBuildRule buildRule(String packagePathString, String... srcFilePaths) {
    Path packagePath = workspace.resolve(packagePathString);
    ImmutableSet<Path> srcFiles =
        Arrays.stream(srcFilePaths)
            .map(srcFile -> packagePath.resolve(srcFile))
            .collect(toImmutableSet());

    return new ProjectBuildRule(srcFiles, packagePath, workspace);
  }

  /** Creates file on the given virtual system and returns the path object for said source file. */
  private void writeFiles(String... filePaths) throws IOException {
    for (String filePath : filePaths) {
      Files.createFile(workspace.resolve(filePath));
    }
  }

  private static FileSystem createDefaultFileSystem() {
    return Jimfs.newFileSystem(Configuration.forCurrentPlatform().toBuilder().build());
  }

  private <T> MutableGraph<T> newGraph(Class<T> unusedClazz) {
    return GraphBuilder.directed().allowsSelfLoops(false).build();
  }
}
