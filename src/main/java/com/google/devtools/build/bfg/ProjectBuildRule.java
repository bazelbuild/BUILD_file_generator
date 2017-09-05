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
import static com.google.common.hash.Hashing.farmHashFingerprint64;
import static java.util.stream.Collectors.joining;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.common.hash.HashFunction;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

final class ProjectBuildRule implements BuildRule {

  /** Strongly connected component of source files. */
  private final ImmutableSet<Path> srcFiles;

  /** List of buildozer commands (sans add deps) to generate for this build rule */
  private final List<String> buildozerCommands;

  /** Hashing function used to determine a component's target name * */
  private static final HashFunction HASH_FUNCTION = farmHashFingerprint64();

  private final Logger logger = Logger.getLogger(ProjectBuildRule.class.getName());

  /** The rule label for this build rule. Of the form "//packagename:targetname" */
  private final String ruleLabel;

  private final String targetName;

  /** Path to the project's WORKSPACE file */
  private final Path workspacePath;

  /** Absolute Path of the rule's package */
  private final Path packagePath;

  // Assumes that all src file paths are absolute
  ProjectBuildRule(ImmutableSet<Path> srcFiles, Path packagePath, Path workspacePath) {
    this.srcFiles = srcFiles;
    this.workspacePath = workspacePath;
    this.packagePath = packagePath;
    targetName = createTargetName();
    ruleLabel = "//" + createPackageName(packagePath) + ":" + targetName;
    buildozerCommands = createBuildozerCommands();
  }

  @Override
  public String label() {
    return ruleLabel;
  }

  @Override
  public List<String> getCreatingBuildozerCommands() {
    return buildozerCommands;
  }

  @Override
  public boolean shouldAddDeps() {
    return true;
  }

  /**
   * Creates the "new rule" command and "add srcs" command, then returns them as a list of strings
   */
  private List<String> createBuildozerCommands() {
    return ImmutableList.of(
        BuildozerCommand.newRule(guessRuleType(), targetName, createPackageName(packagePath)),
        BuildozerCommand.addAttribute("srcs", createSrcsAttribute(), ruleLabel));
  }

  /**
   * Determines the target name. The target name is expected to be relatively unique in comparison
   * to other targets in the same package.
   */
  private String createTargetName() {
    if (srcFiles.size() == 1) {
      return createTargetNameForSingleFile(srcFiles.iterator().next());
    }
    String combinedName =
        srcFiles.stream().map(src -> src.getFileName().toString()).collect(joining(""));
    return "JavaBuildRule_" + HASH_FUNCTION.hashUnencodedChars(combinedName).toString();
  }

  /**
   * Creates a unique target names for a single file component. To prevent collisions, we find its
   * relative path from the BUILD file, and then augment the filename with the relative path.
   *
   * <p>For example, if the relative path is com/google/Hello.java, then the target will be named
   * com-google-Hello. This guards against the situation that there is another single file rule
   * containing com/Hello.java
   */
  private String createTargetNameForSingleFile(Path file) {
    Path relPath = packagePath.relativize(file);
    return Streams.stream(relPath).map(p -> p.toString()).collect(joining("-")).split("\\.")[0];
  }

  /** Determines the package name. */
  private String createPackageName(Path buildFilePath) {
    return workspacePath.relativize(buildFilePath).toString();
  }

  /**
   * Guesses the type of a rule using extremely crude heuristics. If the rule contains at least one
   * source file containing the substring Test.java, then we guess that it is a java_test
   */
  private String guessRuleType() {
    return (srcFiles.stream().anyMatch(path -> path.toString().endsWith("Test.java")))
        ? "java_test"
        : "java_library";
  }

  /**
   * Determines the srcs attribute using a set of strongly connected source files.
   *
   * <p>Temporarily, we are adding the constraint that all source files must be in the same
   * directory. Otherwise, a runtime exception is thrown.
   */
  private ImmutableList<String> createSrcsAttribute() {
    if (!hasAllFilesInOneDirectory(srcFiles)) {
      String files = Joiner.on("\n\t").join(srcFiles);
      logger.warning("ಠ_ಠ All files in component are not in the same root directory:\n\t" + files);
    }
    return srcFiles
        .stream()
        .map(srcFile -> packagePath.relativize(srcFile).toString())
        .sorted()
        .collect(toImmutableList());
  }

  /**
   * Temporarily we are adding the constraint that all source files within a component must be in
   * the same directory. If they are not, then the component is not well formed.
   */
  private boolean hasAllFilesInOneDirectory(ImmutableSet<Path> srcFiles) {
    Path parentPath = srcFiles.iterator().next().getParent();
    return srcFiles.stream().allMatch(path -> path.getParent().equals(parentPath));
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ProjectBuildRule)) {
      return false;
    }
    if (o == this) {
      return true;
    }
    ProjectBuildRule other = (ProjectBuildRule) o;
    return workspacePath.equals(other.workspacePath) && srcFiles.equals(other.srcFiles);
  }

  @Override
  public int hashCode() {
    return Objects.hash(workspacePath, srcFiles);
  }

  @Override
  public String toString() {
    return label();
  }
}
