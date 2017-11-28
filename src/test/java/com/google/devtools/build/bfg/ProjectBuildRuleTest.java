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
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import protos.com.google.devtools.build.bfg.Bfg.TargetInfo;

/** Tests for {@link ProjectBuildRule}. */
@RunWith(JUnit4.class)
public class ProjectBuildRuleTest {

  private static final Path DEFAULT_WORKSPACE = Paths.get("/workspace");

  @Test
  public void singleFileLibraryRule() {
    ProjectBuildRule actual =
        newBuildRule("/workspace/java/package/", ImmutableMap.of("/workspace/java/package/Example.java", TargetInfoUtilities.javaLibrary()));

    assertThat(actual.label()).isEqualTo("//java/package:Example");
    assertThat(actual.getCreatingBuildozerCommands())
        .containsExactly(
            "new java_library Example|//java/package:__pkg__",
            "add srcs Example.java|//java/package:Example");
  }

  /**
   * Ensure that a collapsed single file rule's name is unique to its package. We accomplish this by
   * augmenting the filename with elements from its relative path. For example, if relative to the
   * BUILD file, its path is hi/Example.java, then its target name is hi-Example.
   */
  @Test
  public void collapsedSingleFileRule() {
    ProjectBuildRule actual =
        newBuildRule("/workspace/java/package/", ImmutableMap.of("/workspace/java/package/hi/Example.java", TargetInfoUtilities.javaLibrary()));

    assertThat(actual.label()).isEqualTo("//java/package:hi-Example");
    assertThat(actual.getCreatingBuildozerCommands())
        .containsExactly(
            "new java_library hi-Example|//java/package:__pkg__",
            "add srcs hi/Example.java|//java/package:hi-Example");
  }

  /**
   * Tests behavior when a component has multiple source files. Because we define the target name to
   * be JavaLibrary + some hash code, we are unable to test the actual the buildozer commands.
   * However, we can assert that we do not create incorrect java libraries.
   */
  @Test
  public void multipleFileLibraryRule() {
    ProjectBuildRule actual =
        newBuildRule(
            "/workspace/java/package/",
            ImmutableMap.of("/workspace/java/package/Example.java", TargetInfoUtilities.javaLibrary(),
            "/workspace/java/package/Other.java", TargetInfoUtilities.javaLibrary()));

    assertThat(actual.label()).matches(Pattern.compile("//java/package:JavaBuildRule\\w+"));

    List<String> buildozerCommands = actual.getCreatingBuildozerCommands();
    assertThat(buildozerCommands.get(0))
        .matches("^new java_library JavaBuildRule\\w+\\|//java\\/package:__pkg__");

    assertThat(buildozerCommands.get(1))
        .matches("^add srcs Example\\.java Other\\.java\\|//java/package:JavaBuildRule\\w+");

    assertThat(buildozerCommands).hasSize(2);
  }

  /**
   * Ensures that BFG correctly identifies a test rule. If a rule contains a source file with the
   * ending Test.java, then we will assume it is a java_test
   */
  @Test
  public void guessTestRule_singleFileTestRule() {
    ProjectBuildRule actual =
        newBuildRule("/workspace/java/package/", ImmutableMap.of("/workspace/java/package/ExampleTest.java", TargetInfoUtilities.javaTest()));

    assertThat(actual.label()).isEqualTo("//java/package:ExampleTest");
    assertThat(actual.getCreatingBuildozerCommands())
        .containsExactly(
            "new java_test ExampleTest|//java/package:__pkg__",
            "add srcs ExampleTest.java|//java/package:ExampleTest");
  }

  /**
   * When a component has multiple source files, and even one file is guessed to be a test, then we
   * will consider the entire component as a java_test
   */
  @Test
  public void guessTestRule_multipleFileTestRule() {
    ProjectBuildRule actual =
        newBuildRule(
            "/workspace/java/package/",
            ImmutableMap.of("/workspace/java/package/SomeTest.java", TargetInfoUtilities.javaTest(),
            "/workspace/java/package/Other.java", TargetInfoUtilities.javaLibrary()));

    assertThat(actual.label()).matches(Pattern.compile("//java/package:JavaBuildRule\\w+"));

    assertThat(actual.getCreatingBuildozerCommands().get(0))
        .matches(Pattern.compile("^new java_test JavaBuildRule\\w+\\|//java\\/package:__pkg__"));

    assertThat(actual.getCreatingBuildozerCommands().get(1))
        .matches(
            Pattern.compile(
                "^add srcs Other\\.java SomeTest\\.java\\|//java/package:JavaBuildRule\\w+"));

    assertThat(actual.getCreatingBuildozerCommands()).hasSize(2);
  }

  /**
   * Tests behavior when a component has multiple source files AND one of these files is in a child
   * subdirectory. The add srcs buildozer command should consist of various file paths relative to
   * the BUILD file location. For files in children subdirectories, it will be
   * subdirectory/filename.java
   */
  @Test
  public void ruleWithFilesInChildrenSubdirectories() {
    ProjectBuildRule actual =
        newBuildRule(
            "/workspace/package/",
            ImmutableMap.of("/workspace/package/Hello.java", TargetInfoUtilities.javaLibrary(),
            "/workspace/package/src/Other.java", TargetInfoUtilities.javaLibrary()));

    assertThat(actual.label()).matches(Pattern.compile("//package:JavaBuildRule\\w+"));

    List<String> buildozerCommands = actual.getCreatingBuildozerCommands();
    assertThat(buildozerCommands.get(0))
        .matches("^new java_library JavaBuildRule\\w+\\|//package:__pkg__");

    assertThat(buildozerCommands.get(1))
        .matches("^add srcs Hello\\.java src/Other\\.java\\|//package:JavaBuildRule\\w+");

    assertThat(buildozerCommands).hasSize(2);
  }

  /**
   * Tests behavior when a component has source files spanning multiple directories. The rule's
   * package should be in the lowest common root between the input files. For example, suppose a
   * rule contains /root/bar/Bar.java and /root/foo/Foo.java, then the package should be //root and
   * not //root/bar/ or //root/foo/
   */
  @Test
  public void ruleWithFilesInMultipleDirectories() {
    ProjectBuildRule actual =
        newBuildRule("/workspace/x/", ImmutableMap.of("/workspace/x/foo/Foo.java", TargetInfoUtilities.javaLibrary(), "/workspace/x/bar/Bar.java", TargetInfoUtilities.javaLibrary()));

    assertThat(actual.label()).matches("//x:JavaBuildRule\\w+");

    List<String> buildozerCommands = actual.getCreatingBuildozerCommands();
    assertThat(buildozerCommands.get(0))
        .matches("^new java_library JavaBuildRule\\w+\\|//x:__pkg__");

    assertThat(buildozerCommands.get(1))
        .matches("^add srcs bar/Bar\\.java foo/Foo\\.java\\|//x:JavaBuildRule\\w+");
  }

  private static ProjectBuildRule newBuildRule(String packagePathString, ImmutableMap<String, TargetInfo> srcToTargetInfo) {
    Path packagePath = Paths.get(packagePathString);
    ImmutableMap.Builder<Path, TargetInfo> pathToTargetInfo = ImmutableMap.builder();
    for (Map.Entry<String, TargetInfo> e : srcToTargetInfo.entrySet()) {
      pathToTargetInfo.put(packagePath.resolve(e.getKey()), e.getValue());
    }

    return new ProjectBuildRule(pathToTargetInfo.build(), packagePath, DEFAULT_WORKSPACE);
  }

  /** Tests the heuristics for determining the rule type from the targets in a connected component.
   *
   */
  @Test
  public void guessRuleTypeTest() {
    for (String prefix: ImmutableSet.of("java", "py", "scala")) {
      assertRuleType("binary", prefix, "library", "binary");
      assertRuleType("test", prefix, "library", "test");
      assertRuleType("image", prefix,  "library", "image");
      assertRuleType("image", prefix, "library", "binary", "image");
    }

    assertRuleTypeFailure("java_test", "java_library", "java_binary", "java_image");
    assertRuleTypeFailure("java_library", "py_library");
  }

  private void assertRuleType(String expectedRuleKind, String prefix, String... targetRuleKinds) {
    List<TargetInfo> targets = Stream.of(targetRuleKinds).map(s -> TargetInfo.newBuilder().setRuleKind(prefix + "_" + s).build()).collect(Collectors.toList());
    assertThat(ProjectBuildRule.guessRuleType(targets)).isEqualTo(prefix + "_" + expectedRuleKind);
  }

  private void assertRuleTypeFailure(String... targetRuleKinds) {
    List<TargetInfo> targets = Stream.of(targetRuleKinds).map(s -> TargetInfo.newBuilder().setRuleKind(s).build()).collect(Collectors.toList());
    try {
      String expected = ProjectBuildRule.guessRuleType(targets);
      fail(String.format("%s should not have been returned. Expected exception.", expected));
    } catch (IllegalArgumentException iae) {
      assertThat(iae).hasMessageThat().isNotEmpty();
    }
  }
}
