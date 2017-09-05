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

import com.google.common.collect.ImmutableMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Attempts to map fully qualified top level class names to source files in the file system. If it
 * is unable to map a class to a corresponding source file, then it will add it to a set of
 * unresolved class names.
 *
 * <p>We do not provide support for files with multiple top level classes or package protected
 * classes with a different name than their enclosing source file.
 */
class SourceFileResolver {

  /**
   * Given a list a class files of the form "packageName.className" and a list of potential content
   * roots, attempts to map each class file to a source file in the file system. Any unresolved
   * classes are added to their own set.
   */
  static ImmutableMap<String, Path> resolve(Iterable<String> classList, List<Path> roots) {
    Map<String, Path> classToSourceFileMap = new HashMap<>();
    for (String className : classList) {
      Optional<Path> filePath = resolveForFile(className, roots);
      if (filePath.isPresent()) {
        classToSourceFileMap.put(className, filePath.get());
      }
    }
    return ImmutableMap.copyOf(classToSourceFileMap);
  }

  /**
   * Given the relative file name for a class and a list of content roots, this method searches the
   * file system to find the absolute path for the given file.
   *
   * <p>If no such file exists, we return the UNRESOLVED_FILE token.
   */
  private static Optional<Path> resolveForFile(String className, List<Path> roots) {
    for (Path rootDirectory : roots) {
      Path possiblePath = pathForClassName(className, rootDirectory);
      if (Files.exists(possiblePath)) {
        return Optional.of(possiblePath.toAbsolutePath());
      }
    }
    return Optional.empty();
  }

  /**
   * Takes as input a class name string like "sample.package.car.taco.className" and a path for the
   * content roots such as /google/com/java/ and outputs the absolute path of a potential source
   * file.
   *
   * <p>In the above example, this would be /google/com/java/sample/package/car/taco/className.java
   */
  private static Path pathForClassName(String className, Path root) {
    Path result = root;
    for (String element : splitClassName(className)) {
      result = result.resolve(element);
    }
    return result;
  }

  /**
   * Given class name string like "sample.package.car.taco.className$extra" returns the array
   * {"sample", "package", "car", "className.java"}
   */
  private static String[] splitClassName(String className) {
    String[] subDirectoryArray = className.split("\\.");
    subDirectoryArray[subDirectoryArray.length - 1] += ".java";
    return subDirectoryArray;
  }
}
