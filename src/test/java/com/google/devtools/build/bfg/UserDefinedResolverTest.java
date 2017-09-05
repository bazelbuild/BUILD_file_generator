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
import static junit.framework.TestCase.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link UserDefinedResolver}. */
@RunWith(JUnit4.class)
public class UserDefinedResolverTest {

  @Test
  public void basicMappingNoInnerClass() {
    ImmutableList<String> lines =
        ImmutableList.of(
            "com.test.stuff,//java/com/test/stuff:target",
            "com.test.other,//java/com/test/other:target");

    ImmutableMap<String, BuildRule> actual =
        (new UserDefinedResolver(lines))
            .resolve(ImmutableSet.of("com.test.stuff", "com.test.other"));

    assertThat(actual)
        .containsExactly(
            "com.test.stuff",
            ExternalBuildRule.create("//java/com/test/stuff:target"),
            "com.test.other",
            ExternalBuildRule.create("//java/com/test/other:target"));
  }

  /** Tests situation when only a subset of classes are to be mapped. */
  @Test
  public void filteredMapping() {
    ImmutableList<String> lines =
        ImmutableList.of(
            "com.test.stuff,//java/com/test/stuff:target",
            "com.test.hello,//java/com/test/other:target");

    ImmutableMap<String, BuildRule> actual =
        (new UserDefinedResolver(lines)).resolve(ImmutableSet.of("com.test.stuff"));

    assertThat(actual)
        .containsExactly(
            "com.test.stuff", ExternalBuildRule.create("//java/com/test/stuff:target"));
    assertThat(actual).doesNotContainKey("com.test.hello");
  }

  /** Tests behavior when inner class is mapped to a rule. A runtime exception should be thrown. */
  @Test
  public void innerClassMapped() {
    ImmutableList<String> lines =
        ImmutableList.of(
            "com.test.stuff,//java/com/test/stuff:target",
            "com.test.stuff$Hello,//java/com/test/other:target");
    try {
      (new UserDefinedResolver(lines))
          .resolve((ImmutableSet.of("com.test.stuff", "com.test.stuff$Hello")));
      fail("Expected an exception, but nothing was thrown");
    } catch (IllegalArgumentException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo("Class names must not contain $:com.test.stuff$Hello");
    }
  }

  /**
   * Tests behavior when a class is mapped to multiple rules. A runtime exception should be thrown.
   */
  @Test
  public void classMappedToMultipleRules() {
    ImmutableList<String> lines =
        ImmutableList.of(
            "com.test.stuff,//java/com/test/stuff:target",
            "com.test.stuff,//java/com/test/other:target");
    try {
      (new UserDefinedResolver(lines)).resolve((ImmutableSet.of("com.test.stuff")));
      fail("Expected an exception, but nothing was thrown");
    } catch (IllegalArgumentException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              "com.test.stuff mapped to multiple targets: //java/com/test/other:target, "
                  + "//java/com/test/stuff:target");
    }
  }
}
