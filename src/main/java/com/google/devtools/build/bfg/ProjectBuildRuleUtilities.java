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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Comparator.comparingInt;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;

/** Collection of methods useful for generating white listed build rules. */
class ProjectBuildRuleUtilities {

  /**
   * This method associates directories with the path of their enclosing package. For example, if
   * rules for the source files in /a/b/c/ are written in /a/b/BUILD, then the map will contain the
   * entry /a/b/c/ -> /a/b/. Similarly if the rules for source files in /a/b/c/ are written in
   * /a/b/c/BUILD, the map would contain the entry /a/b/c/ -> /a/b/c/.
   *
   * <p>We decide what BUILD file a directories source files are written through a two phase
   * process. First, we create a disjoint set of directories. Two directories are unioned if any
   * rule contains source files from both directories. Then, we map each directory in a disjoint set
   * to the longest common prefix of all directories in the disjoint set.
   */
  static ImmutableMap<Path, Path> mapDirectoriesToPackages(
      Iterable<ImmutableSet<Path>> components) {

    StandardUnionFind<Path> uf = new StandardUnionFind<>();
    for (ImmutableSet<Path> component : components) {
      ImmutableSet<Path> uniqueDirectories =
          component.stream().map(file -> file.getParent()).collect(toImmutableSet());
      Path firstDir = uniqueDirectories.iterator().next();
      uniqueDirectories.forEach(dir -> uf.union(firstDir, dir));
    }
    ImmutableMap.Builder<Path, Path> directoryToBuildFileMap = ImmutableMap.builder();
    for (Set<Path> equivalenceClass : uf.allEquivalenceClasses()) {
      Path buildFilePath = longestCommonPrefixPath(equivalenceClass);
      equivalenceClass.forEach(dir -> directoryToBuildFileMap.put(dir, buildFilePath));
    }
    return directoryToBuildFileMap.build();
  }

  /**
   * Given a list of absolute paths, computes and returns their longest common path prefix. For
   * example, given the paths /a/b/c/Hi.java and /a/b/d/Bye.java, it would return /a/b/
   */
  static Path longestCommonPrefixPath(Collection<Path> filePaths) {
    checkState(!filePaths.isEmpty());
    if (filePaths.size() == 1) {
      return filePaths.iterator().next();
    }
    ImmutableList<ImmutableList<Path>> pathList =
        filePaths
            .stream()
            .map(path -> ImmutableList.copyOf((Iterable<Path>) path))
            .collect(toImmutableList());

    int maxPrefixLen = longestCommonPrefixLength(pathList);

    ImmutableList<Path> firstPathObject = pathList.get(0);
    checkState(firstPathObject.size() >= maxPrefixLen);

    // Start from root to ensure absolute structure of prefix is not lost
    Path commonPath = filePaths.iterator().next().toAbsolutePath().getRoot();
    for (int i = 0; i < maxPrefixLen; i++) {
      commonPath = commonPath.resolve(firstPathObject.get(i));
    }
    return commonPath;
  }

  /**
   * Computes the length of the longest common path prefix. This value is computed in O(nm) time and
   * O(1) space where n is the number of string array and m is the size of the longest prefix.
   */
  @VisibleForTesting
  static int longestCommonPrefixLength(ImmutableList<ImmutableList<Path>> pathList) {
    int prefixLenUpperBound = pathList.stream().min(comparingInt(ImmutableList::size)).get().size();

    for (int prefixIdx = 0; prefixIdx < prefixLenUpperBound; prefixIdx++) {
      Path curr = pathList.get(0).get(prefixIdx);
      for (ImmutableList<Path> path : pathList) {
        if (!curr.equals(path.get(prefixIdx))) {
          return prefixIdx;
        }
      }
    }
    return prefixLenUpperBound;
  }
}
