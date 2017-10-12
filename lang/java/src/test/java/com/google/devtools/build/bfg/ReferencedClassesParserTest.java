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

import static com.google.common.collect.Iterables.find;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toMap;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.devtools.build.bfg.ReferencedClassesParser.ImportDeclaration;
import com.google.devtools.build.bfg.ReferencedClassesParser.Metadata;
import com.google.devtools.build.bfg.ReferencedClassesParser.QualifiedName;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ReferencedClassesParserTest {

  private static Joiner joiner = Joiner.on("\n");

  private Path srcMain;
  private Path srcTest;

  @Before
  public void setUp() throws IOException {
    FileSystem fileSystem =
        Jimfs.newFileSystem(Configuration.forCurrentPlatform().toBuilder().build());
    srcMain = fileSystem.getPath("/src/main/");
    Files.createDirectories(srcMain);
    srcTest = fileSystem.getPath("/src/test/");
    Files.createDirectories(srcTest);
  }

  private ReferencedClassesParser parse(String source) {
    ReferencedClassesParser parser =
        new ReferencedClassesParser("filename.java", source, ImmutableList.of(srcMain, srcTest));
    assertTrue(
        "Compilation errors: " + parser.compilationMessages, parser.compilationMessages.isEmpty());
    return parser;
  }

  @Test
  public void testErrorMessages() {
    String source =
        joiner.join(
            "class Dummy {",
            "  void method() {",
            "    new ClassB(ClassA.class);",
            "    new ClassA()",
            "  }",
            "}");
    ReferencedClassesParser parser =
        new ReferencedClassesParser("filename.java", source, ImmutableList.of(srcMain, srcTest));
    assertThat(parser.compilationMessages).isNotEmpty();
  }

  @Test
  public void testExtractClassNameFromQualifiedName() {
    assertThat(
            ReferencedClassesParser.extractClassNameFromQualifiedName(
                "ExternalClassA.ExternalAInnerClass"))
        .isEqualTo("ExternalClassA");
    assertThat(
            ReferencedClassesParser.extractClassNameFromQualifiedName(
                "com.google.common.ExternalClassA.ExternalAInnerClass"))
        .isEqualTo("com.google.common.ExternalClassA");
    assertThat(ReferencedClassesParser.extractClassNameFromQualifiedName("innerTemplate"))
        .isEqualTo("");
  }

  @Test
  public void noDuplicates() {
    String source =
        joiner.join(
            "import foo.Bar;",
            "import foo.Bar;",
            "class Dummy {",
            "  void method() {",
            "    new ClassB(ClassA.class);",
            "    new ClassA();",
            "  }",
            "}");
    ReferencedClassesParser parser = parse(source);
    assertThat(parser.importDeclarations).hasSize(1);
    assertThat(parser.symbols.keySet()).containsExactly("ClassA", "ClassB");
  }

  @Test
  public void returnValuesAreConsidered() {
    String source = joiner.join("class Dummy {", "  ClassA method() {", "  }", "}");
    ReferencedClassesParser parser = parse(source);
    assertThat(parser.symbols.keySet()).containsExactly("ClassA");
  }

  @Test
  public void staticMethodsCauseClassesToBeIncluded() {
    String source =
        joiner.join(
            "class Dummy {",
            "  void method() {",
            "    ImmutableList.of();",
            "    com.google.common.collect.ImmutableMap.of();",
            "    ExternalClassA.ExternalAInnerClass.create();",
            "    com.google.common.ExternalClassB.ExternalBInnerClass.create();",
            "    System.out.println();",
            "    tempDeclaration.get();",
            "    TAG_TYPES_TO_FILTER.run();",
            "  }",
            "}");
    ReferencedClassesParser parser = parse(source);
    assertThat(parser.symbols.keySet())
        .containsExactly(
            "com.google.common.collect.ImmutableMap",
            "ImmutableList",
            "ExternalClassA",
            "com.google.common.ExternalClassB");
  }

  @Test
  public void fieldAccessCausesClassesToBeIncluded() {
    String source =
        joiner.join(
            "import com.company.OuterClass.InnerClass.SomeEnum;",
            "class Dummy {",
            "  void method() {",
            "    int i = com.google.common.ExternalClassC.SOME_CONSTANT;",
            "    Object j = ExternalClassD.SOME_CONSTANT;",
            "  }",
            "}");
    ReferencedClassesParser parser = parse(source);
    assertThat(parser.symbols.keySet())
        .containsExactly("com.google.common.ExternalClassC", "ExternalClassD");
  }

  @Test
  public void returnsAnnotations() {
    String source =
        joiner.join(
            "class Dummy {",
            "  @VisibleForTesting",
            "  @Module(injects = Bla.class)",
            "  @DefinedInSameFile",
            "  void method() { }",
            "",
            "  @interface DefinedInSameFile { }",
            "}");
    ReferencedClassesParser parser = parse(source);
    assertThat(parser.symbols.keySet()).containsExactly("VisibleForTesting", "Module", "Bla");
  }

  @Test
  public void ignoresInnerClassesAndSelf() {
    String source =
        joiner.join(
            "package com.company;",
            "import com.google.common.InnerClass;",
            "class Dummy {",
            "  void method() {",
            "    InnerClass.run();",
            "    InnerClass.InnerInnerEnum.run();",
            "    Dummy.staticMethod();",
            "    com.company.Dummy.InnerClass.run();",
            "  }",
            "",
            "  public static class InnerClass {",
            "    public enum InnerInnerEnum {",
            "    }",
            "  }",
            "}");
    ReferencedClassesParser parser = parse(source);
    assertThat(parser.symbols).isEmpty();
  }

  @Ignore("TODO(b/37213985): Unignore when resolved.")
  @Test
  public void ignoresInnerClassesCalledFromLambda() {
    String source =
        joiner.join(
            "class Foo {",
            "  static class UnsupportedException { }",
            "  void foo() {",
            "    Consumer<?> a = o -> new UnsupportedException();",
            "  }",
            "}");
    ReferencedClassesParser parser = parse(source);
    assertThat(parser.symbols.keySet()).containsExactly("Consumer");
  }

  @Test
  public void ignoresInnerClassesInSynchronized() {
    String source =
        joiner.join(
            "class Dummy {",
            "  private void f(OtherClass otherClass) {",
            "    synchronized (otherClass) {",
            "      Class c = InnerClass.class;",
            "    }",
            "  }  ",
            "  public static class InnerClass { }",
            "}");
    ReferencedClassesParser parser = parse(source);
    assertThat(parser.symbols.keySet()).containsExactly("OtherClass");
  }

  @Test
  public void packageNameIsReturned() {
    String source = joiner.join("package com.company;", "class Bla { }");
    ReferencedClassesParser parser = parse(source);
    assertThat(parser.packageName).isEqualTo("com.company");
    assertThat(ReferencedClassesParser.getPackageOfJavaFile(source)).isEqualTo("com.company");
  }

  // TODO(bazel-team): Fix code and enable this test.
  @Ignore
  @Test
  public void returnClassNamesContainingStaticImports() {
    String source =
        joiner.join(
            "import static com.google.common.base.Preconditions.checkNotNull;",
            "import java.util.*;",
            "import static com.google.common.collect.Iterables.*;",
            "class A {",
            "  A() { checkNotNull(); }",
            "}");
    ReferencedClassesParser parser = parse(source);
    assertThat(parser.symbols.keySet())
        .containsExactly(
            "com.google.common.base.Preconditions", "com.google.common.collect.Iterables");
  }

  @Test
  public void ignoreClassesWhenInScopeOfDeclaredTypeParameters() {
    String source =
        joiner.join(
            "class Foo<T> implements java.util.List<T> {",
            "  class Bar<T> {",
            "    void bla(T t) {}",
            "  }",
            "  void bla(T t) {}",
            "  <U> void genericBla(U u) { U u = null; }",
            "}");
    ReferencedClassesParser parser = parse(source);
    assertThat(parser.symbols.keySet()).containsNoneOf("U", "T");
    assertThat(parser.symbols.keySet()).containsExactly("java.util.List");
  }

  @Test
  public void doNotIgnoreTypeParametersAppearingAfterAGenericMethod() {
    String source =
        joiner.join(
            "class Foo {",
            "  <U> void genericBla(U u) { U u = null; }",
            "  abstract U concreteBla();",
            "}");
    ReferencedClassesParser parser = parse(source);
    assertThat(parser.symbols.keySet()).contains("U");
  }

  @Test
  public void testClassName() {
    String source =
        joiner.join(
            "package com.company;",
            "class Dummy {",
            "  public static class InnerClass {",
            "    public enum InnerInnerEnum {",
            "    }",
            "  }",
            "}");
    ReferencedClassesParser parser = parse(source);
    assertThat(parser.className).isEqualTo("Dummy");
  }

  @Test
  public void testExtractTypeNameWithoutGenerics() {
    AST ast = AST.newAST(AST.JLS4);
    org.eclipse.jdt.core.dom.SimpleName entry = ast.newSimpleName("Entry");
    ParameterizedType map = ast.newParameterizedType(ast.newSimpleType(ast.newSimpleName("Map")));
    map.typeArguments().add(ast.newSimpleType(ast.newSimpleName("Foo")));
    QualifiedType type = ast.newQualifiedType(map, entry);
    assertThat(type.toString()).isEqualTo("Map<Foo>.Entry");
    assertThat(ReferencedClassesParser.extractTypeNameWithoutGenerics(type)).isEqualTo("Map.Entry");
  }

  @Test
  public void innerTypesOfGenericTypes() {
    String source =
        joiner.join("class A {", "  void foo(Map<Domain, Range>.Entry<Foo, Bar> bar) {}", "}");
    ReferencedClassesParser parser = parse(source);
    assertThat(parser.symbols.keySet()).containsExactly("Map", "Domain", "Range", "Foo", "Bar");
  }

  @Test
  public void throwsClauseIsProcessed() {
    String source =
        joiner.join(
            "class A {", "  void foo() throws IOException, java.io.FileNotFoundException {}", "}");
    ReferencedClassesParser parser = parse(source);
    assertThat(parser.symbols.keySet())
        .containsExactly("IOException", "java.io.FileNotFoundException");
  }

  @Test
  public void allCapsFieldNamesAreNotConsideredClassNames() {
    String source =
        joiner.join(
            "class A {",
            "  FormattingLogger LOG = null;",
            "  void foo() {",
            "    LOG.warning();",
            "  }",
            "}");
    ReferencedClassesParser parser = parse(source);
    assertThat(parser.symbols.keySet()).containsExactly("FormattingLogger");
  }

  @Test
  public void dontReportJavaLangInnerClasses() {
    ReferencedClassesParser parser = parse("class A extends Thread.UncaughtExceptionHandler { }");
    assertThat(parser.symbols.keySet()).doesNotContain("Thread");
    assertThat(parser.symbols.keySet()).isEmpty();
  }

  @Test
  public void dontReportJavaLangImports() {
    ReferencedClassesParser parser = parse("import static java.lang.String.format;");
    for (ImportDeclaration decl : parser.importDeclarations) {
      assertThat(decl.name().value()).isNotEqualTo("java.lang.String.format");
    }
    assertThat(parser.importDeclarations).isEmpty();
  }

  @Test
  public void reportJavaLangSubpackageImports() {
    ReferencedClassesParser parser = parse("import java.lang.reflect.Method;");
    assertThat(parser.importDeclarations)
        .containsExactly(
            ImportDeclaration.create(
                QualifiedName.create("java.lang.reflect.Method", Metadata.create(1, 7, false)),
                false /* isStatic */));
  }

  @Test
  public void returnsStartPosition() {
    String source =
        joiner.join(
            "import static com.Foo.f;",
            "import com.Bar;",
            "class A {",
            "  FormattingLogger log = null;",
            "  void foo() {",
            "    Map m;",
            "    com.Foo f;",
            "  }",
            "}");
    ReferencedClassesParser parser = parse(source);
    assertTypePosition(parser, "FormattingLogger", 4, 2);
    assertTypePosition(parser, "Map", 6, 4);
    assertImportPosition(parser, "com.Bar", 2, 7);
    assertImportPosition(parser, "com.Foo.f", 1, 14);
  }

  @Test
  public void java8Features() {
    String source =
        joiner.join("class A {", "  void m() {", "    Function o = (x) -> x;", "  }", "}");
    ReferencedClassesParser parser = parse(source);
    assertThat(parser.symbols.keySet()).containsExactly("Function");
  }

  @Test
  public void fullyQualifiedClassName() {
    assertThat(parse("").fullyQualifiedClassName).isNull();
    assertThat(parse("package foo;").fullyQualifiedClassName).isNull();
    assertThat(parse("class A {}").fullyQualifiedClassName).isEqualTo("A");
    assertThat(parse("package foo; class A {}").fullyQualifiedClassName).isEqualTo("foo.A");
  }

  /**
   * Tests that we report methods encountered in a Java file, but only if they aren't defined in
   * this compilation unit. As a special case, tests that we don't report that 'definedInSameClass'
   * needs to be imported. (the type of its parameter triggers a bug in Eclipse that we work around,
   * so needs testing.)
   */
  @Test
  public void reportMethodInvocations() {
    String source =
        joiner.join(
            "import static com.Foo.f;",
            "import com.Bar;",
            "class A {",
            "  void foo() {",
            "    definedInSameClass(null);",
            "    unknownMethod();",
            "  }",
            "  void definedInSameClass(Abstract.Builder<?> foo) { }",
            "  static class Inner {",
            "    void unknownMethod() {",
            "      foo();",
            "      foo2();",
            "    }",
            "  }",
            "}");
    ReferencedClassesParser parser = parse(source);
    assertThat(parser.symbols.keySet())
        .containsExactly("unknownMethod", "foo2", "Abstract", "Abstract.Builder");
  }

  /**
   * Do not report methods that appear in inner classes that have a superclass: Jade doesn't
   * currently fetch inherited symbols for inner classes, which leads to inherited methods being
   * imported. (b/35660499)
   */
  @Test
  public void dontReportMethodsInInnerClassesWithSuperclass() {
    String source =
        joiner.join(
            "class A {",
            "  static class Inner extends Base {",
            "    void f() {",
            "      foo();",
            "    }",
            "  }",
            "}");
    ReferencedClassesParser parser = parse(source);
    assertThat(parser.symbols.keySet()).doesNotContain("foo");
  }

  /**
   * Do not report methods that appear in anonymous classes: Jade doesn't currently fetch inherited
   * symbols for anonymous classes, which leads to inherited methods being imported. (b/35727475)
   */
  @Test
  public void dontReportMethodsInAnonymousClasses() {
    String source =
        joiner.join(
            "class A {",
            "  void f() {",
            "    new AbstractModule(){",
            "      @Override",
            "      protected void configure() {",
            "        bind();",
            "      }",
            "    };",
            "  }",
            "}");
    ReferencedClassesParser parser = parse(source);
    assertThat(parser.symbols.keySet()).doesNotContain("bind");
  }

  @Test
  public void superClass() {
    String source;

    source = "class A { }";
    assertThat(parse(source).superclass).isNull();

    source = "class A extends Foo { }";
    assertThat(parse(source).superclass).isEqualTo("Foo");

    source = "package com; class A extends Foo { }";
    assertThat(parse(source).superclass).isEqualTo("com.Foo");

    source = joiner.join("import com.Foo;", "class A extends Bar { }");
    assertThat(parse(source).superclass).isEqualTo("Bar");

    source = "class A extends Foo<String> { }";
    assertThat(parse(source).superclass).isEqualTo("Foo");

    source = joiner.join("import com.Foo;", "class A extends Foo { }");
    assertThat(parse(source).superclass).isEqualTo("com.Foo");

    source = "class A extends com.Foo { }";
    assertThat(parse(source).superclass).isEqualTo("com.Foo");

    source = joiner.join("import com.Foo;", "class A extends Foo.Bar { }");
    assertThat(parse(source).superclass).isEqualTo("com.Foo.Bar");

    source = "class A extends com.Foo<String> { }";
    assertThat(parse(source).superclass).isEqualTo("com.Foo");

    source = joiner.join("import com.Foo;", "class A extends Foo<String> { }");
    assertThat(parse(source).superclass).isEqualTo("com.Foo");

    source = joiner.join("import com.Foo;", "class A extends Foo.Bar<String> { }");
    assertThat(parse(source).superclass).isEqualTo("com.Foo.Bar");
  }

  @Test
  public void methodsHaveMethodMetadata() {
    String source = "class A { SomeClass f() { bind(); } }";
    ReferencedClassesParser parser = parse(source);
    assertThat(parser.symbols.get("SomeClass").isMethod()).isFalse();
    assertThat(parser.symbols.get("bind").isMethod()).isTrue();
  }

  @Test
  public void testQualifiedTopLevelNames_basic() {
    String source =
        joiner.join(
            "package foo;",
            "import com.ImportedButNotUsed;",
            "import com.Imported;",
            "class A {",
            "  void f() {",
            "    com.fully.qualified.Name x1 = com.fully.qualified.Static.method();",
            "    Unresolved1 unresolved = new Unresolved2.InnerClass();",
            "    Imported.InnerClass x2;",
            "  }",
            "}");
    ReferencedClassesParser parser = parse(source);

    Map<String, Integer> fqNames =
        parser
            .qualifiedTopLevelNames
            .stream()
            .collect(toMap(n -> n.value(), n -> n.metadata().line()));
    assertThat(fqNames)
        .containsExactly(
            "com.ImportedButNotUsed",
            2,
            "com.Imported",
            3,
            "com.fully.qualified.Name",
            6,
            "com.fully.qualified.Static",
            6);
    assertThat(transform(parser.qualifiedTopLevelNames, n -> n.metadata().line())).isOrdered();

    Map<String, Integer> unresolvedNames =
        parser
            .unresolvedClassNames
            .stream()
            .collect(toMap(n -> n.value(), n -> n.metadata().line()));
    assertThat(unresolvedNames).containsExactly("Unresolved1", 7, "Unresolved2", 7);
    assertThat(transform(parser.unresolvedClassNames, n -> n.metadata().line())).isOrdered();
  }

  /**
   * Scenario: a class is referenced but not imported. We set up the filesystem so that we'll find
   * an appropriate file, and therefore assume that the class is in the same package.
   */
  @Test
  public void testQualifiedTopLevelNames_samePackageFromFS() throws IOException {
    String source =
        joiner.join(
            "package foo;",
            "class A {",
            "  void f() {",
            "    SamePackageClass1.InnerClass a;",
            "    SamePackageClass2.InnerClass b;",
            "  }",
            "}");
    Files.createDirectories(srcMain.resolve("foo"));
    Files.write(srcMain.resolve("foo").resolve("SamePackageClass1.java"), new byte[] {});
    Files.createDirectories(srcTest.resolve("foo"));
    Files.write(srcTest.resolve("foo").resolve("SamePackageClass2.java"), new byte[] {});
    ReferencedClassesParser parser = parse(source);
    Map<String, Integer> fqNames =
        parser
            .qualifiedTopLevelNames
            .stream()
            .collect(toMap(n -> n.value(), n -> n.metadata().line()));
    assertThat(fqNames).containsExactly("foo.SamePackageClass1", 4, "foo.SamePackageClass2", 5);
    assertThat(transform(parser.qualifiedTopLevelNames, n -> n.metadata().line())).isOrdered();

    assertThat(parser.unresolvedClassNames).isEmpty();
  }

  /**
   * Asserts that we tolerate non-standard class-names (e.g., org.g_Foo), when they appear in a
   * context that means it must be a class-name (e.g., new org.g_Foo()).
   *
   * <p>Since we return QualifiedName's, which can't currently represent class names that don't have
   * a part starting with a capital letter, we simply ignore them.
   */
  @Test
  public void tolerateNonStandardSimpleClassNames() throws IOException {
    String source =
        joiner.join(
            "package org;", "class A {", "  void f() {", "    new org.g_Foo();", "  }", "}");
    ReferencedClassesParser parser = parse(source);
    assertThat(parser.qualifiedTopLevelNames).isEmpty();
    assertThat(parser.unresolvedClassNames).isEmpty();
  }

  private void assertImportPosition(
      ReferencedClassesParser parser, String importName, int line, int column) {
    Metadata pos =
        find(parser.importDeclarations, imprt -> imprt.name().value().equals(importName))
            .name()
            .metadata();
    assertThat(pos.line()).isEqualTo(line);
    assertThat(pos.column()).isEqualTo(column);
  }

  private void assertTypePosition(
      ReferencedClassesParser parser, String type, int line, int column) {
    Metadata pos = parser.symbols.get(type);
    assertThat(pos.line()).isEqualTo(line);
    assertThat(pos.column()).isEqualTo(column);
  }
}
