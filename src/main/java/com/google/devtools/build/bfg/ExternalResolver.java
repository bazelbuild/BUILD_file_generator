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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableMap;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Set;

/**
 * A Resolver that executes a binary, passes into it the set of classnames to resolve, and reads its
 * output into a map classname --> BuildRule.
 */
public class ExternalResolver implements ClassToRuleResolver {
  private final String executable;

  ExternalResolver(String executable) {
    this.executable = executable;
  }

  @Override
  public ImmutableMap<String, BuildRule> resolve(Set<String> classNames) {
    if (classNames.isEmpty()) {
      return ImmutableMap.of();
    }
    ImmutableMap.Builder<String, BuildRule> result = ImmutableMap.builder();

    ProcessBuilder pb = new ProcessBuilder();
    pb.command().add(executable);
    Process p;
    try {
      p = pb.start();
      try (BufferedWriter out =
          new BufferedWriter(new OutputStreamWriter(p.getOutputStream(), UTF_8))) {
        for (String className : classNames) {
          out.write(className);
        }
      }

      BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream(), UTF_8));
      while (true) {
        String classname = in.readLine();
        String buildRule = in.readLine();

        if (classname == null || buildRule == null) {
          break;
        }

        result.put(classname, ExternalBuildRule.create(buildRule));
      }

      p.waitFor();
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
      return ImmutableMap.of();
    }

    return result.build();
  }
}
