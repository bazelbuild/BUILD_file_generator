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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Iterables.any;
import static com.google.common.collect.Iterables.getLast;
import static com.google.common.collect.Iterables.getOnlyElement;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readAllBytes;

import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import com.google.devtools.build.bfg.ReferencedClassesParser.Metadata;
import com.google.devtools.build.bfg.ReferencedClassesParser.QualifiedName;
import com.google.devtools.build.bfg.ReferencedClassesParser.SimpleName;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Given a set of source files, parses the source files and constructs a class dependency graph */
public class JavaSourceFileParser {

  private final ImmutableList<Path> absoluteSourceFilePaths;

  private final ImmutableList<Path> contentRoots;

  private final Set<String> unresolvedClassNames;

  /**
   * e.g., "com.Foo" --> "org.junit.Test".
   *
   * <p>(u, v) in G iff 'u' mentioned 'v' in code.
   */
  private final ImmutableGraph<String> classToClass;

  /**
   * Maps classes to the files that define them, e.g., "com.Foo" --> "src/main/java/com/Foo.java".
   */
  private final ImmutableMap<String, String> classToFile;

  /**
   * Maps file names to the Bazel rule kinds we want created for them.
   *
   * <p>For example, a class we think is a test should be in a 'java_test', whereas non-test classes
   * should be in a 'java_library'. A class containing a 'static main(String[])' function should be
   * in a 'java_binary'.
   *
   * <p>E.g., "src/main/java/com/FooTest.java" -> "java_test".
   */
  private final ImmutableMap<String, String> filesToRuleKind;

  /**
   * Content roots where BFG should generate one-rule-per-package, instead of one-rule-per-file. For
   * example, if one wants all rules in src/main/ to be rule||package, but src/test/ to be
   * rule||file, this field should contain exactly "src/main/".
   */
  private final ImmutableSet<Path> oneRulePerPackageRoots;

  /**
   * @param oneRulePerPackageRoots Content roots where BFG should generate one-rule-per-package,
   *     instead of one-rule-per-file. See {@link #oneRulePerPackageRoots}.
   */
  JavaSourceFileParser(
      ImmutableList<Path> sourceFilePaths,
      ImmutableList<Path> contentRoots,
      ImmutableSet<Path> oneRulePerPackageRoots)
      throws IOException {
    this.absoluteSourceFilePaths =
        sourceFilePaths
            .stream()
            .map(p -> p.toAbsolutePath().normalize())
            .collect(toImmutableList());
    this.contentRoots = contentRoots;
    this.oneRulePerPackageRoots =
        oneRulePerPackageRoots
            .stream()
            .map(p -> p.toAbsolutePath().normalize())
            .collect(toImmutableSet());

    ImmutableSet.Builder<String> unresolvedClassNames = ImmutableSet.builder();
    MutableGraph<String> classToClass = GraphBuilder.directed().allowsSelfLoops(false).build();
    ImmutableMap.Builder<String, String> classToFile = ImmutableMap.builder();
    ImmutableMap.Builder<String, String> filesToRuleKind = ImmutableMap.builder();
    parseFiles(
        absoluteSourceFilePaths,
        contentRoots,
        oneRulePerPackageRoots,
        unresolvedClassNames,
        classToClass,
        classToFile,
        filesToRuleKind);
    this.classToClass = ImmutableGraph.copyOf(classToClass);
    this.classToFile = classToFile.build();
    this.filesToRuleKind = filesToRuleKind.build();
    this.unresolvedClassNames = unresolvedClassNames.build();
  }

  /** Set of classes that we could not determine a fully qualified name for */
  ImmutableSet<String> getUnresolvedClassNames() {
    return ImmutableSet.copyOf(unresolvedClassNames);
  }

  /**
   * e.g., "com.Foo" --> "org.junit.Test".
   *
   * <p>(u, v) in G iff 'u' mentioned 'v' in code.
   */
  ImmutableGraph<String> getClassToClass() {
    return classToClass;
  }

  /**
   * Maps classes to the files that define them, e.g., "com.Foo" --> "src/main/java/com/Foo.java".
   */
  public ImmutableMap<String, String> getClassToFile() {
    return classToFile;
  }

  /**
   * Maps file names to the Bazel rule kinds we want created for them.
   *
   * <p>For example, a class we think is a test should be in a 'java_test', whereas non-test classes
   * should be in a 'java_library'. A class containing a 'static main(String[])' function should be
   * in a 'java_binary'.
   *
   * <p>E.g., "src/main/java/com/FooTest.java" -> "java_test".
   */
  public ImmutableMap<String, String> getFilesToRuleKind() {
    return filesToRuleKind;
  }

  /** Given a list of source files, creates a graph of their class level dependencies */
  private static void parseFiles(
      ImmutableList<Path> absoluteSourceFilePaths,
      ImmutableList<Path> contentRoots,
      ImmutableSet<Path> oneRulePerPackageRoots,
      ImmutableSet.Builder<String> unresolvedClassNames,
      MutableGraph<String> classToClass,
      ImmutableMap.Builder<String, String> classToFile,
      ImmutableMap.Builder<String, String> fileToRuleKind)
      throws IOException {
    HashMultimap<Path, String> dirToClass = HashMultimap.create();
    for (Path srcFilePath : absoluteSourceFilePaths) {
      ReferencedClassesParser parser =
          new ReferencedClassesParser(
              srcFilePath.getFileName().toString(),
              new String(readAllBytes(srcFilePath), UTF_8),
              contentRoots);
      checkState(parser.isSuccessful);
      if (Strings.isNullOrEmpty(parser.fullyQualifiedClassName)) {
        // The file doesn't contain any classes, skip it. This happens for package-info.java files.
        continue;
      }
      String qualifiedSrc = stripInnerClassFromName(parser.fullyQualifiedClassName);
      dirToClass.put(srcFilePath.getParent(), parser.fullyQualifiedClassName);

      boolean hasEdges = false;
      classToFile.put(qualifiedSrc, srcFilePath.toString());
      for (QualifiedName qualifiedDst : parser.qualifiedTopLevelNames) {
        if (!qualifiedSrc.equals(qualifiedDst.value())) {
          classToClass.putEdge(qualifiedSrc, qualifiedDst.value());
          hasEdges = true;
        }
      }

      for (SimpleName name : parser.unresolvedClassNames) {
        unresolvedClassNames.add(name.value());
      }

      fileToRuleKind.put(
          srcFilePath.toString(),
          decideRuleKind(
              parser, hasEdges ? classToClass.adjacentNodes(qualifiedSrc) : ImmutableSet.of()));
    }

    // Put classes defined in 'oneRulePerPackageRoots' on cycles.
    dirToClass
        .asMap()
        .forEach(
            (path, classes) -> {
              if (any(oneRulePerPackageRoots, p -> path.startsWith(p))) {
                putOnCycle(classes, classToClass);
              }
            });
  }

  private static String decideRuleKind(ReferencedClassesParser parser, Set<String> dependencies) {
    CompilationUnit cu = parser.compilationUnit;
    if (cu.types().isEmpty()) {
      return "java_library";
    }
    AbstractTypeDeclaration topLevelClass = (AbstractTypeDeclaration) cu.types().get(0);
    if ((topLevelClass.getModifiers() & Modifier.ABSTRACT) != 0) {
      // Class is abstract, can't be a test.
      return "java_library";
    }

    // JUnit 4 tests
    if (parser.className.endsWith("Test") && dependencies.contains("org.junit.Test")) {
      return "java_test";
    }

    if (any(
        topLevelClass.bodyDeclarations(),
        d -> d instanceof MethodDeclaration && isMainMethod((MethodDeclaration) d))) {
      return "java_binary";
    }

    return "java_library";
  }

  /**
   * Returns true iff 'methodDeclaration' represents a void static method named 'main' that takes a
   * single String[] parameter.
   */
  private static boolean isMainMethod(MethodDeclaration methodDeclaration) {
    // Is it static?
    if ((methodDeclaration.getModifiers() & Modifier.STATIC) == 0) {
      return false;
    }
    // Does it return void?
    Type returnType = methodDeclaration.getReturnType2();
    if (!returnType.isPrimitiveType()) {
      return false;
    }
    if (((PrimitiveType) returnType).getPrimitiveTypeCode() != PrimitiveType.VOID) {
      return false;
    }
    // Is it called 'main'?
    if (!"main".equals(methodDeclaration.getName().getIdentifier())) {
      return false;
    }
    // Does it have a single parameter?
    if (methodDeclaration.parameters().size() != 1) {
      return false;
    }

    // Is the parameter's type String[]?
    SingleVariableDeclaration pt =
        getOnlyElement((List<SingleVariableDeclaration>) methodDeclaration.parameters());
    IVariableBinding vb = pt.resolveBinding();
    if (vb == null) {
      return false;
    }
    ITypeBinding tb = vb.getType();
    return tb != null && "java.lang.String[]".equals(tb.getQualifiedName());
  }

  /** Create a cycle in 'graph' comprised of classes from 'classes'. */
  private static void putOnCycle(Collection<String> classes, MutableGraph<String> graph) {
    if (classes.size() == 1) {
      return;
    }

    ImmutableList<String> sortedClasses = Ordering.natural().immutableSortedCopy(classes);

    for (int i = 1; i < sortedClasses.size(); i++) {
      graph.putEdge(sortedClasses.get(i - 1), sortedClasses.get(i));
    }
    graph.putEdge(getLast(sortedClasses), sortedClasses.get(0));
  }

  /**
   * Given an arbitrary class name, if the class name is fully qualified, then it returns the fully
   * qualified name of the top level class. If the fully qualified name has no package, or if it is
   * a simple name, then it simply returns the top level class name.
   */
  private static String stripInnerClassFromName(String className) {
    QualifiedName topLevelQualifiedName =
        QualifiedName.create(className, Metadata.EMPTY).getTopLevelQualifiedName();
    return topLevelQualifiedName.value().isEmpty()
        ? className.split("\\.")[0]
        : topLevelQualifiedName.value();
  }
}
