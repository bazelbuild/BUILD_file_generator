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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.devtools.build.bfg.BuildozerCommandCreator.computeBuildozerCommands;
import static com.google.devtools.build.bfg.BuildozerCommandCreator.getBuildFilesForBuildozer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BuildozerCommandCreator}. */
@RunWith(JUnit4.class)
public class BuildozerCommandCreatorTest {

  private static final Path DEFAULT_WORKSPACE = Paths.get("/workspace");

  /**
   * Tests dependency behavior when a single file depends on another file. This should result in two
   * build rules, one with an empty deps field and another with a dependency.
   */
  @Test
  public void depsForSingleFileComponents() {
    MutableGraph<BuildRule> buildRuleGraph = newGraph();
    String packagePath = "/workspace/java/package/";
    ProjectBuildRule src = newProjectBuildRule(packagePath, "/workspace/java/package/Example.java");
    ProjectBuildRule dst = newProjectBuildRule(packagePath, "/workspace/java/package/Other.java");

    buildRuleGraph.putEdge(src, dst);

    ImmutableList<String> actual = computeBuildozerCommands(buildRuleGraph);
    assertThat(actual)
        .containsExactly(
            "new java_library Example|//java/package:__pkg__",
            "add srcs Example.java|//java/package:Example",
            "new java_library Other|//java/package:__pkg__",
            "add srcs Other.java|//java/package:Other",
            "add deps //java/package:Other|//java/package:Example");
  }

  /**
   * Tests dependency behavior when a single file depends on a file in another package/directory.
   * Behavior should not differ from when they were in the same directory.
   */
  @Test
  public void depsForSingleFileComponentsInDifferentPackage() {
    MutableGraph<BuildRule> buildRuleGraph = newGraph();
    ProjectBuildRule src =
        newProjectBuildRule("/workspace/java/package/", "/workspace/java/package/Example.java");
    ProjectBuildRule dst =
        newProjectBuildRule("/workspace/java/other/", "/workspace/java/other/Other.java");

    buildRuleGraph.putEdge(src, dst);

    ImmutableList<String> actual = computeBuildozerCommands(buildRuleGraph);
    assertThat(actual)
        .containsExactly(
            "new java_library Example|//java/package:__pkg__",
            "add srcs Example.java|//java/package:Example",
            "new java_library Other|//java/other:__pkg__",
            "add srcs Other.java|//java/other:Other",
            "add deps //java/other:Other|//java/package:Example");
  }

  /**
   * Tests dependency behavior when a rule depends on multiple rules.
   *
   * <p>We do not want to create an "add deps" command for each dependency. Instead, we want to
   * combine all dependencies into one single buildozer command. e.g.
   *
   * <p>"add deps //package:Target1 //package:Target2|//package:Target3"
   *
   * <p>such that the dependencies are in alphanumeric order.
   *
   * <p>Commands are listed in reverse topological order of the Component DAG. When this is
   * ambiguous (i.e. multiple components can be in the same position), then the commands are ordered
   * by insertion order of components in the original source file graph.
   */
  @Test
  public void depsForTargetWithMultipleDependencies() {
    MutableGraph<BuildRule> buildRuleGraph = newGraph();
    String packagePath = "/workspace/java/package";
    ProjectBuildRule src = newProjectBuildRule(packagePath, "/workspace/java/package/Example.java");
    ProjectBuildRule dstOne = newProjectBuildRule(packagePath, "/workspace/java/package/Other.java");
    ProjectBuildRule dstTwo = newProjectBuildRule(packagePath, "/workspace/java/package/Hello.java");

    buildRuleGraph.putEdge(src, dstOne);
    buildRuleGraph.putEdge(src, dstTwo);

    ImmutableList<String> actual = computeBuildozerCommands(buildRuleGraph);
    assertThat(actual)
        .containsExactly(
            "new java_library Other|//java/package:__pkg__",
            "add srcs Other.java|//java/package:Other",
            "new java_library Hello|//java/package:__pkg__",
            "add srcs Hello.java|//java/package:Hello",
            "new java_library Example|//java/package:__pkg__",
            "add srcs Example.java|//java/package:Example",
            "add deps //java/package:Hello //java/package:Other|//java/package:Example");
    assertThat(actual).doesNotContain("add deps //java/package:Other|//java/package:Example");
    assertThat(actual).doesNotContain("add deps //java/package:Hello|//java/package:Example");
  }

  /**
   * Tests dependency behavior when an external build rule (A) depends on another external build
   * rule (B). There should be no add deps buildozer command for external build rule A.
   */
  @Test
  public void depsForExternalTargetShouldBeEmpty() {
    MutableGraph<BuildRule> buildRuleGraph = newGraph();
    ProjectBuildRule src =
        newProjectBuildRule("/workspace/java/package/", "/workspace/java/package/Example.java");
    ExternalBuildRule dstA = ExternalBuildRule.create("//java/other:Other");
    ExternalBuildRule dstB = ExternalBuildRule.create("//java/something:Something");

    buildRuleGraph.putEdge(src, dstA);
    buildRuleGraph.putEdge(dstA, dstB);

    ImmutableList<String> actual = computeBuildozerCommands(buildRuleGraph);
    assertThat(actual)
        .containsExactly(
            "new java_library Example|//java/package:__pkg__",
            "add srcs Example.java|//java/package:Example",
            "add deps //java/other:Other|//java/package:Example");
  }

  private MutableGraph<BuildRule> newGraph() {
    return GraphBuilder.directed().allowsSelfLoops(false).build();
  }

  /**
   * Given a DAG containing external and project rules, this test ensures Build files are only
   * listed for project rules, and ignored for external rules.
   */
  @Test
  public void getBuildFilesForBuildozer_returnsProjectRule() {
    MutableGraph<BuildRule> buildRuleGraph = newGraph();
    buildRuleGraph.putEdge(
        newProjectBuildRule("/workspace/java/package/", "Example.java"),
        ExternalBuildRule.create("//java/other:Other"));

    Iterable<Path> actual = getBuildFilesForBuildozer(buildRuleGraph, DEFAULT_WORKSPACE);
    assertThat(actual).containsExactly(DEFAULT_WORKSPACE.resolve("java/package/"));
    assertThat(actual).doesNotContain(DEFAULT_WORKSPACE.resolve("java/other/"));
  }

  private static ProjectBuildRule newProjectBuildRule(String packagePathStr, String... srcFiles) {
    Path packagePath = Paths.get(packagePathStr);
    ImmutableSet<Path> srcFilePaths =
        Arrays.stream(srcFiles).map(src -> packagePath.resolve(src)).collect(toImmutableSet());

    return new ProjectBuildRule(srcFilePaths, packagePath, DEFAULT_WORKSPACE);
  }
}
