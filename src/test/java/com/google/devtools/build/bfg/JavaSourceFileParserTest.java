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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
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

/** Tests for {@link JavaSourceFileParser} */
@RunWith(JUnit4.class)
public class JavaSourceFileParserTest {

  private Path workspace;

  @Before
  public void setUp() throws IOException {
    FileSystem fileSystem = createDefaultFileSystem();
    workspace = fileSystem.getPath("/src/");
    Files.createDirectories(workspace);
  }

  @Test
  public void createDepsGraph_e2e_example() throws IOException {
    createSourceFiles("com/hello/", "com/hello/Dummy.java", "com/hello/ClassA.java");
    Path file1 =
        writeFile(
            workspace.resolve("com/hello/Dummy.java"),
            "package com.hello;",
            "import org.external.ClassC;",
            "import com.google.common.annotations.VisibleForTesting;",
            "class Dummy {",
            "  @VisibleForTesting",
            "  void method(ClassA a) {",
            "    new ClassC();",
            "  }",
            "  com.google.Hi method() {}",
            "}");
    Path file2 =
        writeFile(
            workspace.resolve("com/hello/ClassA.java"),
            "package com.hello;",
            "import org.external.second.ClassB;",
            "interface ClassA {",
            "  ClassB function();",
            "}");
    JavaSourceFileParser parser = createParser(file1, file2);

    ImmutableGraph<String> actual = parser.getClassToClass();
    MutableGraph<String> expected = newGraph();
    expected.putEdge("com.hello.Dummy", "com.hello.ClassA");
    expected.putEdge("com.hello.Dummy", "org.external.ClassC");
    expected.putEdge("com.hello.Dummy", "com.google.Hi");
    expected.putEdge("com.hello.ClassA", "org.external.second.ClassB");
    expected.putEdge("com.hello.Dummy", "com.google.common.annotations.VisibleForTesting");

    assertThatGraphsEqual(actual, expected);
    assertThat(parser.getUnresolvedClassNames()).isEmpty();

    assertThat(parser.getClassToFile())
        .containsExactly(
            "com.hello.Dummy",
            "/src/com/hello/Dummy.java",
            "com.hello.ClassA",
            "/src/com/hello/ClassA.java");

    assertThat(parser.getFilesToRuleKind())
        .containsExactly(
            "/src/com/hello/Dummy.java",
            "java_library",
            "/src/com/hello/ClassA.java",
            "java_library");
  }

  @Test
  public void createDepsGraph_importedInnerClass() throws IOException {
    createSourceFiles("com/hello/", "com/hello/Dummy.java");
    Path file1 =
        writeFile(
            workspace.resolve("com/hello/Dummy.java"),
            "package com.hello;",
            "import com.google.Foo.Bar;",
            "class Dummy {",
            "  Bar methodA() {}",
            "}");
    JavaSourceFileParser parser = createParser(file1);

    ImmutableGraph<String> actual = parser.getClassToClass();
    MutableGraph<String> expected = newGraph();
    expected.putEdge("com.hello.Dummy", "com.google.Foo");

    assertThatGraphsEqual(actual, expected);
    assertThat(parser.getUnresolvedClassNames()).isEmpty();
  }

  @Test
  public void createDepsGraph_periodInClassName() throws IOException {
    createSourceFiles("com/hello/", "com/hello/Dummy.java", "com/hello/ThreadSafety.java");
    Path file1 =
        writeFile(
            workspace.resolve("com/hello/Dummy.java"),
            "package com.hello;",
            "import com.hello.ThreadSafety;",
            "class Dummy {",
            "  @ThreadSafety.ThreadHostile",
            "  void methodA() {}",
            "}");
    JavaSourceFileParser parser = createParser(file1);

    ImmutableGraph<String> actual = parser.getClassToClass();
    MutableGraph<String> expected = newGraph();
    expected.putEdge("com.hello.Dummy", "com.hello.ThreadSafety");

    assertThatGraphsEqual(actual, expected);
    assertThat(parser.getUnresolvedClassNames()).isEmpty();
  }

  @Test
  public void createDepsGraph_fileWithNoPackage() throws IOException {
    createSourceFiles(workspace.toString(), "Dummy.java");
    Path file1 =
        writeFile(
            workspace.resolve("Dummy.java"),
            "import com.hello.Foo;",
            "interface Dummy {",
            "   Foo methodA();",
            "}");
    JavaSourceFileParser parser = createParser(file1);

    ImmutableGraph<String> actual = parser.getClassToClass();
    MutableGraph<String> expected = newGraph();
    expected.putEdge("Dummy", "com.hello.Foo");

    assertThatGraphsEqual(actual, expected);
    assertThat(parser.getUnresolvedClassNames()).isEmpty();
  }

  @Test
  public void createDepsGraph_unresolvedClasses() throws IOException {
    createSourceFiles(workspace.toString(), "Dummy.java");
    Path file1 =
        writeFile(workspace.resolve("Dummy.java"), "interface Dummy {", "   Foo methodA();", "}");
    JavaSourceFileParser parser = createParser(file1);

    ImmutableGraph<String> actual = parser.getClassToClass();
    MutableGraph<String> expected = newGraph();

    assertThatGraphsEqual(actual, expected);
    assertThat(parser.getUnresolvedClassNames()).containsExactly("Foo");
  }

  @Test
  public void createDepsGraph_classWithDifferentContentRoot() throws IOException {
    createSourceFiles("java/com/hello/", "java/com/hello/Dummy.java");
    createSourceFiles("test/com/hello/", "test/com/hello/DummyTest.java");
    Path file1 =
        writeFile(
            workspace.resolve("java/com/hello/Dummy.java"), "package com.hello;", "class Dummy {}");
    Path testFile =
        writeFile(
            workspace.resolve("test/com/hello/DummyTest.java"),
            "package com.hello;",
            "class DummyTest {",
            "  public void exampleTest(Dummy A) {}",
            "}");
    ImmutableList<Path> contentRoots =
        ImmutableList.of(workspace.resolve("java/"), workspace.resolve("test/"));
    JavaSourceFileParser parser = createParserWithRoots(contentRoots, file1, testFile);

    ImmutableGraph<String> actual = parser.getClassToClass();
    MutableGraph<String> expected = newGraph();
    expected.putEdge("com.hello.DummyTest", "com.hello.Dummy");

    assertThatGraphsEqual(actual, expected);
    assertThat(parser.getUnresolvedClassNames()).isEmpty();
  }

  /**
   * Tests that we correctly match inner classes with imports. For example, "a.b.c.D.E" is imported,
   * and "E.F" is used, and we should correctly avoid returning it in the "unresolved class names".
   */
  @Test
  public void createDepsGraph_innerClasses() throws IOException {
    createSourceFiles("java/com/hello/", "java/com/hello/Dummy.java");
    Path file1 =
        writeFile(
            workspace.resolve("java/com/hello/Dummy.java"),
            "package com.hello;",
            "import com.google.common.collect.ImmutableList;",
            "import a.b.c.D.E;",
            "class Dummy {",
            "  void f() {",
            "    new ImmutableList.Builder<>();",
            "    new E.F();",
            "  }",
            "}");
    ImmutableList<Path> contentRoots = ImmutableList.of(workspace.resolve("java/"));
    JavaSourceFileParser parser = createParserWithRoots(contentRoots, file1);

    ImmutableGraph<String> actual = parser.getClassToClass();
    MutableGraph<String> expected = newGraph();
    expected.putEdge("com.hello.Dummy", "com.google.common.collect.ImmutableList");
    expected.putEdge("com.hello.Dummy", "a.b.c.D");

    assertThatGraphsEqual(actual, expected);
    assertThat(parser.getUnresolvedClassNames()).isEmpty();
  }

  @Test
  public void allFilesInSameDirAreOnACycle() throws IOException {
    Path x = workspace.resolve("x/");
    Path y = workspace.resolve("y/");
    Path z = workspace.resolve("z/");
    Files.createDirectories(x);
    Files.createDirectories(y);
    Files.createDirectories(z);
    Files.createDirectories(workspace.resolve("tests/"));

    writeFile(workspace.resolve("x/A.java"), "package x; class A {}");
    writeFile(workspace.resolve("y/A.java"), "package y; class A {}");
    writeFile(workspace.resolve("y/B.java"), "package y; class B {}");
    writeFile(workspace.resolve("z/A.java"), "package z; class A {}");
    writeFile(workspace.resolve("z/B.java"), "package z; class B {}");
    writeFile(workspace.resolve("z/C.java"), "package z; class C {}");

    writeFile(workspace.resolve("tests/A.java"), "package tests; class A {}");
    writeFile(workspace.resolve("tests/B.java"), "package tests; class B {}");

    ImmutableList<Path> contentRoots = ImmutableList.of(workspace.resolve(""));
    JavaSourceFileParser parser =
        new JavaSourceFileParser(
            ImmutableList.of(
                workspace.resolve("./x/A.java"),
                workspace.resolve("./y/A.java"),
                workspace.resolve("./y/B.java"),
                workspace.resolve("./z/A.java"),
                workspace.resolve("./z/B.java"),
                workspace.resolve("./z/C.java"),
                workspace.resolve("./tests/A.java"),
                workspace.resolve("./tests/B.java")),
            contentRoots,
            ImmutableSet.of(x, y, z) /* oneRulePerPackageRoots */);
    ImmutableGraph<String> actual = parser.getClassToClass();

    MutableGraph<String> expected = newGraph();
    expected.putEdge("y.A", "y.B");
    expected.putEdge("y.B", "y.A");
    expected.putEdge("z.A", "z.B");
    expected.putEdge("z.B", "z.C");
    expected.putEdge("z.C", "z.A");
    // Implicit: there's no tests.A and tests.B here, because 'tests/' is not in
    // oneRulePerPackageRoots.

    assertThatGraphsEqual(actual, expected);
  }

  @Test
  public void tolerateEmptyFiles() throws Exception {
    createSourceFiles("com/hello/", "com/hello/package-info.java");
    Path file = writeFile(workspace.resolve("com/hello/package-info.java"), "package com.hello;");
    JavaSourceFileParser parser = createParser(file);

    assertThatGraphsEqual(parser.getClassToClass(), newGraph());
  }

  @Test
  public void ruleKindDetection() throws Exception {
    createSourceFiles("x/", "x/Binary.java", "x/SomeTest.java", "x/AbstractTest.java");
    Path file1 =
        writeFile(
            workspace.resolve("x/Binary.java"),
            "package com.hello;",
            "class Binary {",
            "  public static void main(String[] args) { }",
            "}");
    Path file2 =
        writeFile(
            workspace.resolve("x/SomeTest.java"),
            "package com.hello;",
            "class SomeTest {",
            "  @org.junit.Test",
            "  void testSomething() {}",
            "}");
    Path file3 =
        writeFile(
            workspace.resolve("x/AbstractTest.java"),
            "package com.hello;",
            "abstract class AbstractTest {",
            "  @org.junit.Test",
            "  abstract void testSomething() {}",
            "}");
    JavaSourceFileParser parser = createParser(file1, file2, file3);

    assertThat(parser.getFilesToRuleKind())
        .containsExactly(
            // Binary.java has a static method named 'main' with a single String[] parameter.
            "/src/x/Binary.java",
            "java_binary",
            "/src/x/SomeTest.java",
            "java_test",
            // AbstractTest is abstract, so must be a java_library.
            "/src/x/AbstractTest.java",
            "java_library");
  }

  private void createSourceFiles(String dir, String... filePaths) throws IOException {
    Files.createDirectories(workspace.resolve(dir));
    for (String filePathString : filePaths) {
      Path filePath = workspace.resolve(filePathString);
      Files.createFile(filePath);
    }
  }

  private static Path writeFile(Path filePath, String... lines) throws IOException {
    String content = Joiner.on("\n").join(lines);
    return Files.write(filePath, content.getBytes(UTF_8));
  }

  private static void assertThatGraphsEqual(Graph<String> actual, Graph<String> expected) {
    assertThat(actual.nodes()).containsExactlyElementsIn(expected.nodes());
    assertThat(actual.edges()).containsExactlyElementsIn(expected.edges());
  }

  private static MutableGraph<String> newGraph() {
    return GraphBuilder.directed().allowsSelfLoops(false).build();
  }

  private JavaSourceFileParser createParser(Path... srcfiles) throws IOException {
    return createParserWithRoots(ImmutableList.of(workspace), srcfiles);
  }

  private static JavaSourceFileParser createParserWithRoots(
      ImmutableList<Path> roots, Path... srcfiles) throws IOException {
    return new JavaSourceFileParser(
        ImmutableList.copyOf(srcfiles), roots, ImmutableSet.of() /* oneRulePerPackageRoots */);
  }

  private static FileSystem createDefaultFileSystem() {
    return Jimfs.newFileSystem(Configuration.forCurrentPlatform().toBuilder().build());
  }
}
