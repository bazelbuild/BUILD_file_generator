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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.common.hash.HashFunction;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import protos.com.google.devtools.build.bfg.Bfg.Strings;
import protos.com.google.devtools.build.bfg.Bfg.TargetInfo;

final class ProjectBuildRule implements BuildRule {

  /** Strongly connected component of source files with their target info. */
  private final ImmutableMap<Path, TargetInfo> srcFilesToTargetInfo;

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
  ProjectBuildRule(ImmutableMap<Path, TargetInfo> srcFilesToTargetInfo, Path packagePath, Path workspacePath) {
    this.srcFilesToTargetInfo = srcFilesToTargetInfo;
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
    TargetInfo merged = mergeTargets(srcFilesToTargetInfo.values());
    return ImmutableList.<String>builder()
        .add(BuildozerCommand.newRule(merged.getRuleKind(), targetName, createPackageName(packagePath)))
        .add(BuildozerCommand.addAttribute("srcs", createSrcsAttribute(), ruleLabel))
        .addAll(merged.getBuildozerCommands().getElementsList().stream().map(cmd -> BuildozerCommand.addFragment(cmd, ruleLabel)).collect(Collectors.toSet()))
        .build();
  }

  /**
   * Determines the target name. The target name is expected to be relatively unique in comparison
   * to other targets in the same package.
   */
  private String createTargetName() {
    if (srcFilesToTargetInfo.size() == 1) {
      return createTargetNameForSingleFile(Iterables.getOnlyElement(srcFilesToTargetInfo.keySet()));
    }
    String combinedName =
        srcFilesToTargetInfo.keySet().stream().map(src -> src.getFileName().toString()).collect(joining(""));
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

  private static TargetInfo mergeTargets(Collection<TargetInfo> targetInfos) {
    String ruleKind = guessRuleType(targetInfos);
    Strings.Builder cmds = Strings.newBuilder();
    targetInfos.forEach(ti -> cmds.mergeFrom(ti.getBuildozerCommands()));
    return TargetInfo.newBuilder().setRuleKind(ruleKind).setBuildozerCommands(cmds).build();
  }

  /**
   * Guesses the type of a rule using extremely crude heuristics.
   * <ul>
   * <li>If the component contains only one type of rule, we use that.</li>
   * <li>If the set of rule kinds contains <pre>[rule-prefix]_library</pre> and <pre>[rule-prefix]_test</pre>,
   * we use <pre>[rule_prefix]_test</pre>. </li>
   * <li>If the set of rule kinds contains <pre>[rule-prefix]_library</pre> and <pre>[rule-prefix]_binary</pre>, we use
   * <pre>[rule_prefix]_binary</pre>.</li>
   *
   * If none of those match, we error out (for now).
   * </ul>
   */
  @VisibleForTesting
  static String guessRuleType(Collection<TargetInfo> targetInfos) {
    ImmutableSet<String> ruleKinds = ImmutableSet.copyOf(targetInfos.stream().map(ti -> ti.getRuleKind()).collect(Collectors.toSet()));
    if (ruleKinds.size() == 1) {
      return Iterables.getOnlyElement(ruleKinds);
    }
    Set<String> ruleKindPrefixes = ruleKinds.stream().map(rk -> rk.substring(0, rk.indexOf('_'))).collect(Collectors.toSet());
    if (ruleKindPrefixes.size() != 1) {
      throw new IllegalArgumentException("Different rule kind prefixes in a single component. targetInfos=" + targetInfos);
    }
    Set<String> ruleKindSuffixes = ruleKinds.stream().map(rk -> rk.substring(rk.indexOf('_') + 1)).collect(Collectors.toSet());
    if (ImmutableSet.of("library", "test").equals(ruleKindSuffixes)) {
      return Iterables.getOnlyElement(ruleKindPrefixes) + "_test";
    }
    if (ImmutableSet.of("library", "binary").equals(ruleKindSuffixes)) {
      return Iterables.getOnlyElement(ruleKindPrefixes) + "_binary";
    }
    if (ruleKindSuffixes.contains("image") && Sets.filter(ruleKindSuffixes, rk -> !ImmutableSet.of("binary", "library", "image").contains(rk)).isEmpty()) {
      return Iterables.getOnlyElement(ruleKindPrefixes) + "_image";
    }
    throw new IllegalArgumentException("Unable to determine rule kind to use. targetInfos=" + targetInfos);
  }

  /**
   * Determines the srcs attribute using a set of strongly connected source files.
   *
   * <p>Temporarily, we are adding the constraint that all source files must be in the same
   * directory. Otherwise, a runtime exception is thrown.
   */
  private ImmutableList<String> createSrcsAttribute() {
    if (!hasAllFilesInOneDirectory(srcFilesToTargetInfo.keySet())) {
      String files = Joiner.on("\n\t").join(srcFilesToTargetInfo.keySet());
      logger.warning("ಠ_ಠ All files in component are not in the same root directory:\n\t" + files);
    }
    return srcFilesToTargetInfo.keySet()
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
    return workspacePath.equals(other.workspacePath) && srcFilesToTargetInfo.equals(other.srcFilesToTargetInfo);
  }

  @Override
  public int hashCode() {
    return Objects.hash(workspacePath, srcFilesToTargetInfo);
  }

  @Override
  public String toString() {
    return label();
  }
}
