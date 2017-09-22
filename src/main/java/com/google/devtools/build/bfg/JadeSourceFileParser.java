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
import static com.google.common.collect.Iterables.any;
import static com.google.common.collect.Iterables.getLast;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readAllBytes;

import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import com.google.devtools.build.bfg.ReferencedClassesParser.Metadata;
import com.google.devtools.build.bfg.ReferencedClassesParser.QualifiedName;
import com.google.devtools.build.bfg.ReferencedClassesParser.SimpleName;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Given a set of source files, parses the source files and constructs a class dependency graph */
public class JadeSourceFileParser {

  private final ImmutableList<Path> absoluteSourceFilePaths;

  private final ImmutableList<Path> contentRoots;

  private final Set<String> unresolvedClassNames;

  private final ImmutableGraph<String> classDependencyGraph;

  /**
   * Content roots where BFG should generate one-rule-per-package, instead of one-rule-per-file. For
   * example, if one wants all rules in src/main/ to be rule||package, but src/test/ to be
   * rule||file, this field should contain exactly "src/main/".
   */
  private final ImmutableSet<Path> oneRulePerPackageRoots;

  /**
   * @param oneRulePerPackageRoots Content roots where BFG should generate one-rule-per-package,
   *     instead of one-rule-per-file. See {@link #oneRulePerPackageRoots}.
   */
  JadeSourceFileParser(
      ImmutableList<Path> absoluteSourceFilePaths,
      ImmutableList<Path> contentRoots,
      ImmutableSet<Path> oneRulePerPackageRoots)
      throws IOException {
    this.absoluteSourceFilePaths = absoluteSourceFilePaths;
    this.contentRoots = contentRoots;
    this.oneRulePerPackageRoots = oneRulePerPackageRoots;
    unresolvedClassNames = new HashSet<>();
    classDependencyGraph = createClassDependencyGraph();
  }

  /** Set of classes that we could not determine a fully qualified name for */
  ImmutableSet<String> getUnresolvedClassNames() {
    return ImmutableSet.copyOf(unresolvedClassNames);
  }

  /** class dependency graph constructed from the provided source files */
  ImmutableGraph<String> getClassDependencyGraph() {
    return classDependencyGraph;
  }

  /** Given a list of source files, creates a graph of their class level dependencies */
  private ImmutableGraph<String> createClassDependencyGraph() throws IOException {
    HashMultimap<Path, String> dirToClass = HashMultimap.create();
    MutableGraph<String> graph = GraphBuilder.directed().allowsSelfLoops(false).build();
    for (Path srcFilePath : absoluteSourceFilePaths) {
      ReferencedClassesParser parser =
          new ReferencedClassesParser(
              srcFilePath.getFileName().toString(),
              new String(readAllBytes(srcFilePath), UTF_8),
              contentRoots);
      checkState(parser.isSuccessful);
      if (Strings.isNullOrEmpty(parser.fullyQualifiedClassName)) {
        // The file doesn't contain any classes, skip it. This happens for package-info.java files.
        continue;
      }
      String qualifiedSrc = stripInnerClassFromName(parser.fullyQualifiedClassName);
      dirToClass.put(srcFilePath.getParent(), parser.fullyQualifiedClassName);
      for (QualifiedName qualifiedDst : parser.qualifiedTopLevelNames) {
        if (!qualifiedSrc.equals(qualifiedDst.value())) {
          graph.putEdge(qualifiedSrc, qualifiedDst.value());
        }
      }
      for (SimpleName name : parser.unresolvedClassNames) {
        unresolvedClassNames.add(name.value());
      }
    }

    // Put classes defined in 'oneRulePerPackageRoots' on cycles.
    dirToClass
        .asMap()
        .forEach(
            (path, classes) -> {
              if (any(oneRulePerPackageRoots, p -> path.startsWith(p))) {
                putOnCycle(classes, graph);
              }
            });

    return ImmutableGraph.copyOf(graph);
  }

  /** Create a cycle in 'graph' comprised of classes from 'classes'. */
  private void putOnCycle(Collection<String> classes, MutableGraph<String> graph) {
    if (classes.size() == 1) {
      return;
    }

    ImmutableList<String> sortedClasses = Ordering.natural().immutableSortedCopy(classes);

    for (int i = 1; i < sortedClasses.size(); i++) {
      graph.putEdge(sortedClasses.get(i - 1), sortedClasses.get(i));
    }
    graph.putEdge(getLast(sortedClasses), sortedClasses.get(0));
  }

  /**
   * Given an arbitrary class name, if the class name is fully qualified, then it returns the fully
   * qualified name of the top level class. If the fully qualified name has no package, or if it is
   * a simple name, then it simply returns the top level class name.
   */
  private static String stripInnerClassFromName(String className) {
    QualifiedName topLevelQualifiedName =
        QualifiedName.create(className, Metadata.EMPTY).getTopLevelQualifiedName();
    return topLevelQualifiedName.value().isEmpty()
        ? className.split("\\.")[0]
        : topLevelQualifiedName.value();
  }
}
