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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Maps classnames to rules based on a user-provided map. */
public class UserDefinedResolver implements ClassToRuleResolver {

  private final ImmutableList<String> lines;

  public UserDefinedResolver(List<String> lines) {
    this.lines = ImmutableList.copyOf(lines);
  }

  @Override
  public ImmutableMap<String, BuildRule> resolve(Set<String> classNames) {
    return createTargetMap(classNames);
  }

  /**
   * Takes in a path to a file where each line consists of a "classname,//target." Then uses this to
   * construct a map between classnames and targets.
   *
   * <p>If the path is empty, then it returns an empty map.
   */
  private ImmutableMap<String, BuildRule> createTargetMap(Set<String> classNames) {
    Map<String, BuildRule> targetMap = new LinkedHashMap<>();
    for (String line : lines) {
      String[] words = line.split(",");
      String className = words[0];
      if (!classNames.contains(className)) {
        continue;
      }
      if (className.contains("$")) {
        throw new IllegalArgumentException(
            String.format("Class names must not contain $:%s", className));
      }
      String targetName = words[1];
      BuildRule previousTarget = targetMap.put(className, ExternalBuildRule.create(targetName));
      if (previousTarget != null) {
        throw new IllegalArgumentException(
            String.format(
                "%s mapped to multiple targets: %s, %s",
                className, targetName, previousTarget.label()));
      }
    }
    return ImmutableMap.copyOf(targetMap);
  }
}
