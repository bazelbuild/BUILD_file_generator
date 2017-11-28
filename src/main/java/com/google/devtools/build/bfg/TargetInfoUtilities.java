package com.google.devtools.build.bfg;
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.nio.file.Path;
import protos.com.google.devtools.build.bfg.Bfg;
import protos.com.google.devtools.build.bfg.Bfg.TargetInfo;

public abstract class TargetInfoUtilities {

  public static TargetInfo javaLibrary() {
    return TargetInfo.newBuilder().setRuleKind("java_library").build();
  }

  public static TargetInfo javaTest() {
    return TargetInfo.newBuilder().setRuleKind("java_test").build();
  }

  public static TargetInfo javaBinary(String mainClass) {
    return TargetInfo.newBuilder().setRuleKind("java_binary")
        .setBuildozerCommands(Bfg.Strings.newBuilder().addElements(String.format("set main_class %s", mainClass)).build()).
            build();
  }

  public static ImmutableMap<Path, TargetInfo> javaLibraries(Path... paths) {
    return ImmutableMap.copyOf(Maps.asMap(Sets.newHashSet(paths), p -> javaLibrary()));
  }
}
