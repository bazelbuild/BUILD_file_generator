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
import static com.google.devtools.build.bfg.ClassGraphPreconditions.checkNoInnerClassesPresent;
import static com.google.devtools.build.bfg.ProjectBuildRuleUtilities.mapDirectoriesToPackages;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.graph.Graph;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableGraph;
import com.google.re2j.Pattern;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Maps top level class names to rules. */
class ProjectClassToRuleResolver implements ClassToRuleResolver {

  private final ImmutableGraph<String> classGraph;

  private final ImmutableList<Path> contentRoots;

  private final Pattern whiteList;

  private final Path workspace;

  private final Logger logger = Logger.getLogger(ProjectBuildRule.class.getName());

  /** The maximum percentage of classes that can be unresolved before BFG errors out. */
  static final double UNRESOLVED_THRESHOLD = 0.7;

  ProjectClassToRuleResolver(
      ImmutableGraph<String> classGraph,
      Pattern whiteList,
      ImmutableList<Path> contentRoots,
      Path workspace) {
    checkNoInnerClassesPresent(classGraph.nodes());
    this.contentRoots = contentRoots;
    this.workspace = workspace;
    this.classGraph = classGraph;
    this.whiteList = whiteList;
  }

  @Override
  public ImmutableMap<String, BuildRule> resolve(Set<String> classes) {
    ImmutableSet<String> projectClasses =
        classes.stream().filter(name -> whiteList.matcher(name).find()).collect(toImmutableSet());

    ImmutableMap<String, Path> classToSrcFileMap =
        SourceFileResolver.resolve(projectClasses, contentRoots);

    handleUnresolvedClasses(projectClasses, classToSrcFileMap.keySet());

    ImmutableGraph<String> resolvedClassGraph =
        ImmutableGraph.copyOf(Graphs.inducedSubgraph(classGraph, classToSrcFileMap.keySet()));

    ImmutableGraph<Path> srcFileGraph =
        ClassToSourceGraphConsolidator.map(resolvedClassGraph, classToSrcFileMap);

    ImmutableGraph<ImmutableSet<Path>> componentDAG =
        new StronglyConnectedComponents<>(srcFileGraph).computeComponentDAG();

    return ImmutableMap.copyOf(
        mapClassToBuildRule(componentDAG, classToSrcFileMap, resolvedClassGraph));
  }

  /** Maps each top level class name to a build rule. */
  private Map<String, BuildRule> mapClassToBuildRule(
      ImmutableGraph<ImmutableSet<Path>> componentDAG,
      Map<String, Path> classToSrcFileMap,
      Graph<String> classGraph) {

    ImmutableMap<Path, Path> dirToBuildFileMap = mapDirectoriesToPackages(componentDAG.nodes());
    Map<Path, BuildRule> srcToTargetMap = new HashMap<>();
    for (ImmutableSet<Path> component : componentDAG.nodes()) {
      Path buildFilePath = dirToBuildFileMap.get(component.iterator().next().getParent());
      BuildRule rule = new ProjectBuildRule(component, buildFilePath, workspace);
      component.stream().forEach(src -> srcToTargetMap.put(src, rule));
    }
    ImmutableMap.Builder<String, BuildRule> classToBuildRuleMap = ImmutableMap.builder();
    for (String className : classGraph.nodes()) {
      Path srcFile = classToSrcFileMap.get(className);
      classToBuildRuleMap.put(className, srcToTargetMap.get(srcFile));
    }
    return classToBuildRuleMap.build();
  }

  /**
   * Identifies and logs all unresolved class names. Then computes the percentage of unresolved
   * classes. If more than a certain threshold are unresolved, then it will throw an
   * IllegalStateException.
   */
  private void handleUnresolvedClasses(
      ImmutableSet<String> projectClasses, ImmutableSet<String> resolvedClasses) {
    Set<String> unresolvedClasses = Sets.difference(projectClasses, resolvedClasses);
    if (unresolvedClasses.isEmpty() || projectClasses.isEmpty()) {
      return;
    }

    logger.severe(
        String.format(
            "Unresolved class names = {\n\t%s\n}", Joiner.on("\n\t").join(unresolvedClasses)));

    if (UNRESOLVED_THRESHOLD * projectClasses.size() < unresolvedClasses.size()) {
      throw new IllegalStateException(
          String.format(
              "BUILD File Generator failed to map over %.0f percent of class names. "
                  + "Check your white list and content roots",
              UNRESOLVED_THRESHOLD * 100));
    }
  }
}
