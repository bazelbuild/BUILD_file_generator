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

import static com.google.common.truth.Truth.assertThat;
import static com.google.devtools.build.bfg.ClassNameUtilities.getOuterClassName;
import static com.google.devtools.build.bfg.ClassNameUtilities.isInnerClass;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ClassNameUtilities}. */
@RunWith(JUnit4.class)
public class ClassNameUtilitiesTest {

  @Test
  public void basicInnerClassDetection() {
    assertThat(isInnerClass("com.bfg.hello$Hello")).isTrue();
    assertThat(isInnerClass("com.bfg.hello")).isFalse();
    assertThat(isInnerClass("com.hello.taco$Taco")).isTrue();
    assertThat(isInnerClass("com.hello.taco")).isFalse();
  }

  @Test
  public void obtainOuterClassName() {
    assertThat(getOuterClassName("com.bfg.hello$Hello")).isEqualTo("com.bfg.hello");
    assertThat(getOuterClassName("com.bfg.hello")).isEqualTo("com.bfg.hello");
    assertThat(getOuterClassName("com.hello.taco$Taco")).isEqualTo("com.hello.taco");
    assertThat(getOuterClassName("com.hello.taco")).isEqualTo("com.hello.taco");
  }
}
