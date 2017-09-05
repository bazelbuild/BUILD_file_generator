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

import static com.google.devtools.build.bfg.ClassGraphPreconditions.checkNoInnerClassesPresent;

import com.google.common.collect.ImmutableMap;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Takes as input a directed graph of top level class names, and outputs a graph of build rules. */
class GraphProcessor {

  private final Logger logger = Logger.getLogger(ProjectBuildRule.class.getName());

  private final ImmutableGraph<String> classGraph;

  GraphProcessor(ImmutableGraph<String> classGraph) {
    checkNoInnerClassesPresent(classGraph.nodes());
    this.classGraph = classGraph;
  }

  /**
   * Creates the build rule DAG using provided resolvers. Each resolver maps a top level class name
   * to a build rule. We combine the resolvers to create one huge Map from class to rule.
   *
   * <p>We make the following stipulations. First, a class must not map to more than one build rule.
   * Otherwise, we throw an error. However, a class need not map to any rule. In which case we will
   * not include it in the resulting build rule Graph. Second, the resulting graph must not have
   * cycles.
   *
   * <p>TODO(bazel-team) Report/Crash if the resulting graph has cycles.
   */
  ImmutableGraph<BuildRule> createBuildRuleDAG(Iterable<ClassToRuleResolver> resolvers) {
    ImmutableMap<String, BuildRule> ruleMap = createClassToRuleMap(resolvers);
    MutableGraph<BuildRule> buildRuleDAG = GraphBuilder.directed().allowsSelfLoops(false).build();
    for (String className : classGraph.nodes()) {
      BuildRule srcRule = ruleMap.get(className);
      if (srcRule == null) {
        continue;
      }
      buildRuleDAG.addNode(srcRule);
      for (String successor : classGraph.successors(className)) {
        BuildRule dstRule = ruleMap.get(successor);
        if (dstRule == null || srcRule.equals(dstRule)) {
          continue;
        }
        buildRuleDAG.putEdge(srcRule, dstRule);
      }
    }
    return ImmutableGraph.copyOf(buildRuleDAG);
  }

  private ImmutableMap<String, BuildRule> createClassToRuleMap(
      Iterable<ClassToRuleResolver> resolvers) {
    Map<String, BuildRule> ruleMap = new LinkedHashMap<>();
    Set<String> unresolvedClasses = new HashSet<>(classGraph.nodes());
    for (ClassToRuleResolver resolver : resolvers) {
      if (unresolvedClasses.isEmpty()) {
        break;
      }
      Map<String, BuildRule> map = resolver.resolve(unresolvedClasses);
      unresolvedClasses.removeAll(map.keySet());
      for (Map.Entry<String, BuildRule> entry : map.entrySet()) {
        String className = entry.getKey();
        BuildRule newRule = entry.getValue();
        BuildRule existingRule = ruleMap.put(className, newRule);
        if (existingRule != null) {
          throw new GraphProcessorException(
              String.format(
                  "Class name %s was mapped to two different targets:%s, %s",
                  className, newRule, existingRule));
        }
      }
    }
    if (!unresolvedClasses.isEmpty()) {
      logger.warning(
          String.format(
              "Can't find BUILD rules for the following classes:\n%s",
              unresolvedClasses.stream().sorted().toArray()));
    }
    return ImmutableMap.copyOf(ruleMap);
  }

  // TODO(bazel-team) Improve how you handle exceptions.
  static class GraphProcessorException extends RuntimeException {
    GraphProcessorException(String message) {
      super(message);
    }
  }
}
