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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.graph.Graph;
import java.nio.file.Path;
import java.util.List;

/** Generates a list of buildozer commands, given a DAG of build rules. */
public class BuildozerCommandCreator {

  /** This method creates a list of buildozer commands, given a build rule DAG */
  static ImmutableList<String> computeBuildozerCommands(Graph<BuildRule> buildRuleDAG) {
    ImmutableList.Builder<String> commands = ImmutableList.builder();
    for (BuildRule buildRule : buildRuleDAG.nodes()) {
      List<String> initialCommands = buildRule.getCreatingBuildozerCommands();
      commands.addAll(initialCommands);
      List<String> depsList = getDepsForBuildRule(buildRuleDAG, buildRule);
      if (buildRule.shouldAddDeps() && !depsList.isEmpty()) {
        commands.add(BuildozerCommand.addAttribute("deps", depsList, buildRule.label()));
      }
    }
    return commands.build();
  }

  /** Returns all the directories that need a BUILD file for Buildozer to properly execute */
  static Iterable<Path> getBuildFilesForBuildozer(
      Graph<BuildRule> buildRuleGraph, Path workspacePath) {
    Function<BuildRule, Path> relativePathForRule =
        rule -> workspacePath.resolve(rule.label().split("//")[1].split(":")[0]);

    return buildRuleGraph
        .nodes()
        .stream()
        .filter(buildRule -> buildRule.shouldAddDeps())
        .map(relativePathForRule)
        .collect(toImmutableSet());
  }

  private static List<String> getDepsForBuildRule(Graph<BuildRule> ruleDAG, BuildRule rule) {
    return ruleDAG
        .successors(rule)
        .stream()
        .map(BuildRule::label)
        .sorted()
        .collect(toImmutableList());
  }
}
