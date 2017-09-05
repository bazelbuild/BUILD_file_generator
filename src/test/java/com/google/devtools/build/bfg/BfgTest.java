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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BuildozerCommandCreator}. */
@RunWith(JUnit4.class)
public class BfgTest {

  private FileSystem fileSystem;

  private Path workspace;

  @Before
  public void setUp() throws IOException {
    fileSystem = createDefaultFileSystem();
    workspace = fileSystem.getPath("/src");
  }

  /** Ensures that if a BUILD file does not exist, then it is properly generated. */
  @Test
  public void nonexistentBuildFileGenerated() throws InterruptedException, IOException {
    Path buildFileDirectory = workspace.resolve("test/dir/");
    assertThat(Files.isDirectory(buildFileDirectory)).isFalse();

    Bfg.generateBuildFileIfNecessary(buildFileDirectory);

    assertThat(Files.isDirectory(buildFileDirectory)).isTrue();
    assertThat(Files.exists(buildFileDirectory.resolve("BUILD"))).isTrue();
  }

  /**
   * Ensures that in the situation a BUILD file does not exist, but it's enclosing directory exists,
   * that the BUILD file is generated.
   */
  @Test
  public void existingDirectoryButNoBuildFile() throws InterruptedException, IOException {
    Path buildFileDirectory = workspace.resolve("existing/dir/");
    Path buildFile = buildFileDirectory.resolve("BUILD");

    Files.createDirectories(buildFileDirectory);
    assertThat(Files.exists(buildFile)).isFalse();

    Bfg.generateBuildFileIfNecessary(buildFileDirectory);

    assertThat(Files.exists(buildFile)).isTrue();
  }

  private static FileSystem createDefaultFileSystem() {
    return Jimfs.newFileSystem(Configuration.forCurrentPlatform().toBuilder().build());
  }
}
