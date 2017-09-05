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

import java.util.List;

/** Represents a general Bazel build rule. */
public interface BuildRule {

  /** Returns the label for a given build rule. */
  String label();

  /**
   * Returns a partial list of buildozer commands that will need to be created.
   *
   * <p>This list of commands includes commands such as creating a new target, adding srcs. HOWEVER,
   * it does not contain any commands to add dependencies.
   *
   * <p>For external rules, which we do not want to modify, we return an empty list.
   */
  List<String> getCreatingBuildozerCommands();

  /** Returns true if "add deps" should be called for this build rule. */
  boolean shouldAddDeps();
}
