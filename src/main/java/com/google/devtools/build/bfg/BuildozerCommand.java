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

import com.google.common.base.Joiner;

/** Utility methods to construct Buildozer commands. See go/buildozer for details */
class BuildozerCommand {

  /**
   * Creates a new rule in a build file.
   *
   * @param ruleKind e.g. java_test, cc_test, java_library
   * @param ruleName e.g. Bfg
   * @param packageName e.g. root/package/name:target
   */
  static String newRule(String ruleKind, String ruleName, String packageName) {
    return String.format("new %s %s|//%s:__pkg__", ruleKind, ruleName, packageName);
  }

  /**
   * Adds `values` to `attribute` of rule `target`.
   *
   * <p>For example: {@code addAttribute("deps", ImmutableList.of("dep1"), "//root/pkg/Bfg)"}
   */
  static String addAttribute(String attribute, Iterable<String> values, String target) {
    return String.format("add %s %s|%s", attribute, Joiner.on(" ").join(values), target);
  }

  static String addFragment(String fragment, String target) {
    return String.format("%s|%s", fragment, target);
  }
}
