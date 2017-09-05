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

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link SourceFileResolver}. */
@RunWith(JUnit4.class)
public class SourceFileResolverTest {

  /**
   * Example 1. Given a class name with a .java source file that exists under a content root return
   * that file.
   */
  @Test
  public void oneResolvedClassName() throws IOException {
    FileSystem fs = createDefaultFileSystem();
    Files.createDirectories(fs.getPath("/src", "com"));
    Path existingFilePath = writeFile(fs.getPath("/src", "com", "HotSauce.java"));

    Map<String, Path> actual =
        SourceFileResolver.resolve(
            Arrays.asList("com.HotSauce"), Arrays.asList(fs.getPath("/src")));

    assertThat(actual).containsExactly("com.HotSauce", existingFilePath);
  }

  /**
   * Example 2. Tests behavior when a source file cannot be found for a class name. The unresolved
   * class should not be in the resolvedMap.
   */
  @Test
  public void oneUnresolvedClassName() throws IOException {
    FileSystem fs = createDefaultFileSystem();
    Files.createDirectories(fs.getPath("/src", "com"));
    writeFile(fs.getPath("/src", "com", "HotSauce.java"));

    Map<String, Path> actual =
        SourceFileResolver.resolve(
            Arrays.asList("com.DotFileParser"), Arrays.asList(fs.getPath("/src")));

    assertThat(actual).isEmpty();
    assertThat(actual).doesNotContainKey("com.DotFileParser");
  }

  /** Example 3. Mixed example with one resolved class name and another unresolved class */
  @Test
  public void oneUnresolvedClassName_oneResolvedClassName() throws IOException {
    FileSystem fs = createDefaultFileSystem();
    Files.createDirectories(fs.getPath("/src", "com"));
    Path existingFilePath = writeFile(fs.getPath("/src", "com", "HotSauce.java"));

    Map<String, Path> actual =
        SourceFileResolver.resolve(
            Arrays.asList("com.HotSauce", "com.DotFileParser"), Arrays.asList(fs.getPath("/src")));

    assertThat(actual).containsExactly("com.HotSauce", existingFilePath);
    assertThat(actual).doesNotContainKey("com.DotFileParser");
  }

  /** Creates file on the given virtual system and returns the path object for said source file. */
  private Path writeFile(Path filePath) throws IOException {
    return Files.write(filePath, Arrays.asList("Don't care"));
  }

  private FileSystem createDefaultFileSystem() {
    return Jimfs.newFileSystem(Configuration.forCurrentPlatform().toBuilder().build());
  }
}
