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

/** Set of utility methods for commonly executed methods on class names */
public class ClassNameUtilities {

  /** Returns true if class is an inner class */
  static boolean isInnerClass(String className) {
    return className.contains("$");
  }

  /**
   * Returns the name of the outer class. For top level classes, returns class name. For inner
   * classes, strips the inner class token from fully qualified class name
   */
  static String getOuterClassName(String className) {
    return className.split("\\$")[0];
  }
}
