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

import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ProjectBuildRule}. */
@RunWith(JUnit4.class)
public class ProjectBuildRuleTest {

  private static final Path DEFAULT_WORKSPACE = Paths.get("/workspace");

  @Test
  public void singleFileLibraryRule() {
    ProjectBuildRule actual =
        newBuildRule("/workspace/java/package/", "/workspace/java/package/Example.java");

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
        newBuildRule("/workspace/java/package/", "/workspace/java/package/hi/Example.java");

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
            "/workspace/java/package/Example.java",
            "/workspace/java/package/Other.java");

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
        newBuildRule("/workspace/java/package/", "/workspace/java/package/ExampleTest.java");

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
            "/workspace/java/package/SomeTest.java",
            "/workspace/java/package/Other.java");

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
            "/workspace/package/Hello.java",
            "/workspace/package/src/Other.java");

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
        newBuildRule("/workspace/x/", "/workspace/x/foo/Foo.java", "/workspace/x/bar/Bar.java");

    assertThat(actual.label()).matches("//x:JavaBuildRule\\w+");

    List<String> buildozerCommands = actual.getCreatingBuildozerCommands();
    assertThat(buildozerCommands.get(0))
        .matches("^new java_library JavaBuildRule\\w+\\|//x:__pkg__");

    assertThat(buildozerCommands.get(1))
        .matches("^add srcs bar/Bar\\.java foo/Foo\\.java\\|//x:JavaBuildRule\\w+");
  }

  private static ProjectBuildRule newBuildRule(String packagePathString, String... srcFilePaths) {
    Path packagePath = Paths.get(packagePathString);
    ImmutableSet<Path> srcFiles =
        Arrays.stream(srcFilePaths)
            .map(srcFile -> packagePath.resolve(srcFile))
            .collect(toImmutableSet());

    return new ProjectBuildRule(srcFiles, packagePath, DEFAULT_WORKSPACE);
  }
}
