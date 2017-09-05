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
import static com.google.devtools.build.bfg.ProjectBuildRuleUtilities.longestCommonPrefixLength;
import static com.google.devtools.build.bfg.ProjectBuildRuleUtilities.longestCommonPrefixPath;
import static com.google.devtools.build.bfg.ProjectBuildRuleUtilities.mapDirectoriesToPackages;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Map.Entry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ProjectBuildRuleUtilities}. */
@RunWith(JUnit4.class)
public class ProjectBuildRuleUtilitiesTest {

  @Test
  public void mapDirectories_mapsToOneself() {
    ImmutableSet<ImmutableSet<Path>> components =
        ImmutableSet.of(
            component("/a/A1.java", "/a/A2.java"),
            component("/a/b/B.java"),
            component("/a/b/c/C.java"));
    ImmutableMap<Path, Path> actual = mapDirectoriesToPackages(components);
    assertThat(actual)
        .containsExactlyEntriesIn(
            createMap(entry("/a/", "/a/"), entry("/a/b/", "/a/b/"), entry("/a/b/c/", "/a/b/c/")));
  }

  @Test
  public void mapDirectories_collapsesToParentPackage() {
    ImmutableSet<ImmutableSet<Path>> components =
        ImmutableSet.of(
            component("/a/Hi.java", "/a/b/Foo.java"),
            component("/a/b/Hello.java", "/a/b/c/Bye.java"));
    ImmutableMap<Path, Path> actual = mapDirectoriesToPackages(components);
    assertThat(actual)
        .containsExactlyEntriesIn(
            createMap(entry("/a/", "/a/"), entry("/a/b/", "/a/"), entry("/a/b/c/", "/a/")));
  }

  @Test
  public void longestCommonPrefixLength_filesInSameDirectory() {
    ImmutableList.Builder<ImmutableList<Path>> filePaths = ImmutableList.builder();

    filePaths.add(
        filePath("/java", "com", "google", "Hello.java"),
        filePath("/java", "com", "google", "Greetings.java"));

    assertThat(longestCommonPrefixLength(filePaths.build())).isEqualTo(3);
  }

  @Test
  public void longestCommonPrefixLength_filesInDifferentDirectories() {
    ImmutableList.Builder<ImmutableList<Path>> filePaths = ImmutableList.builder();

    filePaths.add(
        filePath("/java", "com", "google", "Hello.java"),
        filePath("/java", "com", "facebook", "dir", "Greetings.java"));
    assertThat(longestCommonPrefixLength(filePaths.build())).isEqualTo(2);
  }

  /**
   * Ensures that we obtain the longestCommonPrefix path rather than the longestCommonPrefix string
   * For example, suppose we had two paths /a/b and /a/bob. The longest common prefix string would
   * be /a/b but the long common prefix path would simply be /a/b/.
   */
  @Test
  public void longestCommonPrefix_findsCommonPathNotCommonCharacters() {
    ImmutableSet.Builder<Path> filePaths = ImmutableSet.builder();

    filePaths.add(Paths.get("/a", "b", "Hello.java"), Paths.get("/a", "bob", "Greetings.java"));
    Path actual = longestCommonPrefixPath(filePaths.build());
    assertThat((Object) actual).isEqualTo(Paths.get("/a"));
  }

  /** Ensures it is able to find the longest common prefix for a set of source files. */
  @Test
  public void longestCommonPrefix_findsCommonPathForDirectories() {
    ImmutableSet.Builder<Path> filePaths = ImmutableSet.builder();
    filePaths.add(Paths.get("/java/com/"));
    Path actual = longestCommonPrefixPath(filePaths.build());
    assertThat((Object) actual).isEqualTo(Paths.get("/java/com"));
  }

  /** Ensures it is able to find the longest common prefix for a set of directories */
  @Test
  public void longestCommonPrefix_findsCommonPathForSourceFiles() {
    ImmutableSet.Builder<Path> filePaths = ImmutableSet.builder();
    filePaths.add(Paths.get("/java/com/Hello.java"));
    Path actual = longestCommonPrefixPath(filePaths.build());
    assertThat((Object) actual).isEqualTo(Paths.get("/java/com/Hello.java"));
  }

  private static ImmutableList<Path> filePath(String root, String... subdirectories) {
    Path path = Paths.get(root, subdirectories);
    return ImmutableList.copyOf((Iterable<Path>) path);
  }

  private static ImmutableSet<Path> component(String... paths) {
    return Arrays.stream(paths).map(path -> Paths.get(path)).collect(toImmutableSet());
  }

  private static ImmutableMap<Path, Path> createMap(Entry<Path, Path>... entries) {
    ImmutableMap.Builder<Path, Path> map = ImmutableMap.builder();
    Arrays.stream(entries).forEach(entry -> map.put(entry));
    return map.build();
  }

  /** Convenience method because to avoid writing Paths.get */
  private static Entry<Path, Path> entry(String keyPath, String valuePath) {
    return new SimpleEntry<>(Paths.get(keyPath), Paths.get(valuePath));
  }
}
