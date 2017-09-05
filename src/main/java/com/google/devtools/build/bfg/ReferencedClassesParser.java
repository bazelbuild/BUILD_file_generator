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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Predicates.containsPattern;
import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.getLast;
import static java.util.Comparator.comparingInt;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Streams;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.Message;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * Processes a Java file AST and returns a set of class names that the file references and imports.
 */
public class ReferencedClassesParser {

  private static final Joiner DOT_JOINER = Joiner.on(".");
  private static final Splitter DOT_SPLITTER = Splitter.on(".");
  private static final Predicate<CharSequence> isClassName = containsPattern("^[A-Z][a-zA-Z0-9]*$");
  private static final Predicate<CharSequence> isJavaLangClassPredicate =
      containsPattern("^java\\.lang\\.[A-Z][a-zA-Z0-9]*");
  private static final String[] EMPTY_STRING_ARRAY = new String[0];
  public final boolean isSuccessful;
  public final Map<String, Metadata> symbols;
  public final ImmutableSet<ImportDeclaration> importDeclarations;
  public final String packageName;
  public final List<String> compilationMessages;
  public final String className;

  /** The fully-qualified name of the top-level class in this Java file. */
  public final String fullyQualifiedClassName;

  @Nullable public final String superclass;

  /**
   * Simple names from the Java file that couldn't resolve into fully qualified names either through
   * imports or same-package file lookup.
   *
   * <p>The order of iteration is guaranteed to be by appearance order in the Java file.
   */
  public final ImmutableSet<SimpleName> unresolvedClassNames;

  /**
   * Fully qualified top level class names found in this Java file.
   *
   * <p>The order of iteration is guaranteed to be by appearance order in the Java file.
   */
  public final ImmutableSet<QualifiedName> qualifiedTopLevelNames;

  public ReferencedClassesParser(String filename, String source, Collection<Path> contentRoots) {
    this.compilationMessages = getCompilationMessages(filename, source);
    if (!compilationMessages.isEmpty()) {
      this.symbols = Collections.emptyMap();
      this.importDeclarations = ImmutableSet.of();
      this.packageName = "";
      this.className = "";
      this.fullyQualifiedClassName = "";
      this.superclass = "";
      this.unresolvedClassNames = ImmutableSet.of();
      this.qualifiedTopLevelNames = ImmutableSet.of();
      isSuccessful = false;
      return;
    }
    CompilationUnit compilationUnit = parseAndResolveSource(source);

    Visitor visitor = new Visitor(compilationUnit);
    compilationUnit.accept(visitor);

    this.symbols = Maps.filterKeys(visitor.symbols, s -> !isJavaLangClass(s));
    this.importDeclarations =
        distinctByPredicate(visitor.importDeclarations, imprt -> stripMetadata(imprt));
    this.packageName = getPackageOfJavaFile(compilationUnit);

    AbstractTypeDeclaration abstractTypeDeclaration =
        compilationUnit.types().isEmpty()
            ? null
            : (AbstractTypeDeclaration) compilationUnit.types().get(0);
    this.className =
        abstractTypeDeclaration == null ? null : abstractTypeDeclaration.getName().getIdentifier();
    this.fullyQualifiedClassName =
        this.className == null
            ? null
            : DOT_JOINER.skipNulls().join(emptyToNull(this.packageName), this.className);
    if (abstractTypeDeclaration instanceof TypeDeclaration) {
      this.superclass =
          expandTypeNameUsingImports(
              getNameOfType(((TypeDeclaration) abstractTypeDeclaration).getSuperclassType()),
              this.packageName,
              this.importDeclarations);
    } else {
      this.superclass = null;
    }
    ArrayList<SimpleName> unresolvedClassNames = new ArrayList<>();
    ArrayList<QualifiedName> qualifiedTopLevelNames = new ArrayList<>();
    populateFullyQualifiedTopLevelClasses(
        importDeclarations,
        packageName,
        symbols,
        contentRoots,
        unresolvedClassNames,
        qualifiedTopLevelNames);

    this.unresolvedClassNames =
        distinctByPredicate(unresolvedClassNames, n -> n.value())
            .stream()
            .sorted(
                comparingInt((SimpleName n) -> n.metadata().line())
                    .thenComparingInt(n -> n.metadata().column()))
            .collect(toImmutableSet());
    this.qualifiedTopLevelNames =
        distinctByPredicate(qualifiedTopLevelNames, n -> n.value())
            .stream()
            .sorted(
                comparingInt((QualifiedName n) -> n.metadata().line())
                    .thenComparingInt(n -> n.metadata().column()))
            .collect(toImmutableSet());
    isSuccessful = true;
  }

  /**
   * Returns an immutable set containing each of elements, minus duplicates, in the order each
   * appears first in the source collection.
   *
   * <p>Comparison (equals() and hashCode()) are performed on key.apply(X) instead of on X.
   */
  private <T, S> ImmutableSet<T> distinctByPredicate(
      Iterable<? extends T> items, Function<T, S> key) {
    HashMap<S, T> m = new HashMap<>();
    for (T item : items) {
      m.putIfAbsent(key.apply(item), item);
    }
    return ImmutableSet.copyOf(m.values());
  }

  /** Returns an import declaration with the qname's metadata stripped. */
  private ImportDeclaration stripMetadata(ImportDeclaration imprt) {
    return ImportDeclaration.create(
        QualifiedName.create(imprt.name().value(), Metadata.create(0, 0, false)), imprt.isStatic());
  }

  /**
   * Post-processes the 'symbols' we got from the ASTVisitor, to get two lists:
   *
   * <ul>
   *   <li>Fully qualified, top-level classnames that were encountered in the Java file. This is
   *       useful for finding dependencies for these classes.
   *   <li>Simple names from the Java file that couldn't resolve into fully qualified names either
   *       through imports or same-package file lookup.
   * </ul>
   *
   * @param importDeclarations import declarations found in the compilation unit.
   * @param packageName name of the package the Java file resides in.
   * @param symbols all symbols that were found by the ASTVisitor.
   * @param outUnresolvedClassNames OUT a set that gets filled with unresolved simple class names.
   * @param outQualifiedNames OUT a list that gets filled with the fully qualified top-level class
   *     names in the Java file.
   * @param contentRoots paths where Java files reside in the depot, e.g., src/main/ and src/test/.
   */
  private static void populateFullyQualifiedTopLevelClasses(
      Collection<ImportDeclaration> importDeclarations,
      String packageName,
      Map<String, Metadata> symbols,
      Collection<Path> contentRoots,
      ArrayList<SimpleName> outUnresolvedClassNames,
      ArrayList<QualifiedName> outQualifiedNames) {
    Set<String> simpleNameOfImports = new HashSet<>();
    for (ImportDeclaration id : importDeclarations) {
      outQualifiedNames.add(id.name().getTopLevelQualifiedName());
      simpleNameOfImports.add(getLast(id.name().parts()));
    }
    List<Path> potentialPackagePaths = listOfPathsForPackage(packageName, contentRoots);
    for (Map.Entry<String, Metadata> type : symbols.entrySet()) {
      String classname = type.getKey();
      Metadata metadata = type.getValue();
      QualifiedName topLevelQualifiedName =
          QualifiedName.create(classname, metadata).getTopLevelQualifiedName();
      if (!topLevelQualifiedName.value().isEmpty()) {
        outQualifiedNames.add(checkNotNull(topLevelQualifiedName));
        continue;
      }
      if (classname.contains(".")) {
        // We can get here for two reasons - (1) because 'symbols' contains both Foo and Foo.Bar
        // when the file contains Foo.Bar and Foo looks like a top-level class name;
        // and (2) because the entire class name doesn't contain a part which starts with a capital
        // letter.
        // For (1), we can skip, because for Foo.Bar we will process Foo, and we're only interested
        // in top-level names anyway.
        // For (2), QualifiedName doesn't support such names, so we have to skip. (we could change
        // this at some point).
        // Either way we skip.
        continue;
      }
      // Classname is already imported, therefore added in the first loop in this method.
      if (simpleNameOfImports.contains(classname)) {
        continue;
      }
      // Classname is simple - see if it's in the package as ourselves.
      if (doesClassExistInFileSystem(classname, potentialPackagePaths)) {
        outQualifiedNames.add(
            checkNotNull(
                QualifiedName.create(
                    packageName.isEmpty()
                        ? classname
                        : String.format("%s.%s", packageName, classname),
                    metadata)));
      } else {
        outUnresolvedClassNames.add(SimpleName.create(classname, metadata));
      }
    }
  }

  /** Return a list of absolute paths that a package spans */
  private static List<Path> listOfPathsForPackage(String packageName, Collection<Path> roots) {
    String relativePathStr = packageName.replace(".", File.separator);
    return roots.stream().map(root -> root.resolve(relativePathStr)).collect(toImmutableList());
  }

  /**
   * @param className The classname to infer from.
   * @param packagePathList list of absolute paths where files in package can be located
   * @return boolean indicating if the class can be found in the file system
   */
  private static boolean doesClassExistInFileSystem(String className, List<Path> packagePathList) {
    for (Path packagePath : packagePathList) {
      Path possibleSourceFile = packagePath.resolve(String.format("%s.java", className));
      if (Files.exists(possibleSourceFile)) {
        return true;
      }
    }
    return false;
  }

  /** A Visitor that collects classnames from the AST. */
  private static class Visitor extends ASTVisitor {
    private final Map<String, Metadata> symbols;
    private final List<ImportDeclaration> importDeclarations;

    private final CompilationUnit compilationUnit;

    /** A mapping of (class->methods it declared). */
    private final SetMultimap<AbstractTypeDeclaration, String> methodsOfClass;

    private Visitor(CompilationUnit compilationUnit) {
      this.compilationUnit = compilationUnit;
      this.symbols = new HashMap<>();
      this.importDeclarations = new ArrayList<>();
      this.methodsOfClass = HashMultimap.create();
    }

    private void addType(IBinding binding, String type, int startPosition) {
      if (type.isEmpty()) {
        return;
      }

      // If 'binding' refers to anything defined in this compilationUnit, don't add it.
      if (compilationUnit.findDeclaringNode(binding) != null) {
        return;
      }

      symbols.put(
          type,
          Metadata.create(
              compilationUnit.getLineNumber(startPosition),
              compilationUnit.getColumnNumber(startPosition),
              false));
    }

    private void visitExpressionIfName(Expression expression) {
      if (!(expression instanceof Name)) {
        return;
      }
      IBinding binding = ((Name) expression).resolveBinding();
      if (binding != null && binding.getKind() == IBinding.VARIABLE) {
        return;
      }
      addType(
          expression.resolveTypeBinding(),
          extractClassNameFromQualifiedName(((Name) expression).getFullyQualifiedName()),
          expression.getStartPosition());
    }

    @Override
    public boolean visit(SimpleType node) {
      addType(node.resolveBinding(), node.toString(), node.getStartPosition());
      return true;
    }

    @Override
    public boolean visit(MethodInvocation node) {
      visitExpressionIfName(node.getExpression());
      if (node.getExpression() != null) {
        return true;
      }
      // node is of the form `methodName(...)`, and not eg., `Foo.methodName()`.

      org.eclipse.jdt.core.dom.SimpleName simpleName = node.getName();

      if (compilationUnit.findDeclaringNode(simpleName.resolveBinding()) != null) {
        // simpleName is defined somewhere in this compilation unit - so no need to import it.
        return true;
      }

      // Do not report methods that appear in inner/anonymous classes that have a superclass:
      // Jade doesn't currently fetch inherited symbols for inner/anonymous classes, which
      // leads to inherited methods being imported. (b/35660499, b/35727475)
      // This isn't perfect because another class might call a same-named method; if this
      // becomes a problem, I'll use a blacklist.
      AbstractTypeDeclaration containingClass = getContainingClass(node);
      if (!(containingClass.getParent() instanceof CompilationUnit)
          && containingClass instanceof TypeDeclaration
          && ((TypeDeclaration) containingClass).getSuperclassType() != null) {
        return true;
      }
      if (isDescendantOfAnonymousClassDeclaration(node)) {
        return true;
      }

      // Work around Eclipse JDT Bug https://bugs.eclipse.org/bugs/show_bug.cgi?id=462192,
      // where `simpleName.resolveBinding() == null` happens even though 'simpleName' is
      // defined in the current compilation unit.
      Set<String> methods = methodsOfClass.get(containingClass);
      if (methods.isEmpty()) {
        methods.addAll(getMethodDeclarations(containingClass));
      }
      if (!methods.contains(simpleName.getIdentifier())) {
        int startPosition = simpleName.getStartPosition();
        symbols.put(
            simpleName.getIdentifier(),
            Metadata.create(
                compilationUnit.getLineNumber(startPosition),
                compilationUnit.getColumnNumber(startPosition),
                true));
      }
      return true;
    }

    public boolean visitAnnotation(Annotation node) {
      addType(
          node.resolveTypeBinding(),
          node.getTypeName().getFullyQualifiedName(),
          node.getStartPosition());
      return true;
    }

    @Override
    public boolean visit(NormalAnnotation node) {
      return visitAnnotation(node);
    }

    @Override
    public boolean visit(SingleMemberAnnotation node) {
      return visitAnnotation(node);
    }

    @Override
    public boolean visit(MarkerAnnotation node) {
      return visitAnnotation(node);
    }

    @Override
    public boolean visit(QualifiedType node) {
      addType(
          node.resolveBinding(),
          extractClassNameFromQualifiedName(extractTypeNameWithoutGenerics(node)),
          node.getStartPosition());
      return true;
    }

    @Override
    public boolean visit(org.eclipse.jdt.core.dom.ImportDeclaration node) {
      if (node.isOnDemand()) {
        return true;
      }
      String qnameStr = node.getName().getFullyQualifiedName();
      if (isJavaLangClass(qnameStr)) {
        return true;
      }
      int startPosition = node.getName().getStartPosition();
      QualifiedName qname =
          QualifiedName.create(
              qnameStr,
              Metadata.create(
                  compilationUnit.getLineNumber(startPosition),
                  compilationUnit.getColumnNumber(startPosition),
                  false));
      if (qname != null) {
        importDeclarations.add(ImportDeclaration.create(qname, node.isStatic()));
      }
      return true;
    }

    @Override
    public boolean visit(FieldAccess node) {
      visitExpressionIfName(node.getExpression());
      return true;
    }

    @Override
    public boolean visit(org.eclipse.jdt.core.dom.QualifiedName node) {
      if (node.getParent().getNodeType() != ASTNode.IMPORT_DECLARATION) {
        visitExpressionIfName(node.getQualifier());
      }
      return false;
    }
  }

  /**
   * @return a String representation of 'type'. Handles qualified, simple and parameterized types.
   */
  @Nullable
  private String getNameOfType(Type type) {
    if (type instanceof QualifiedType) {
      return extractTypeNameWithoutGenerics((QualifiedType) type);
    }
    if (type instanceof SimpleType) {
      return ((SimpleType) type).getName().getFullyQualifiedName();
    }
    if (type instanceof ParameterizedType) {
      return getNameOfType(((ParameterizedType) type).getType());
    }
    return null;
  }

  /**
   * Expands a possibly simple name to a fully qualified name.
   *
   * <ul>
   *   <li>com.Bla + <whatever> --> com.Bla
   *   <li>Bla + 'import com.Bla' --> com.Bla
   *   <li>Bla + <no imports> + 'package org' --> org.Bla
   * </ul>
   */
  @Nullable
  private static String expandTypeNameUsingImports(
      @Nullable String name,
      @Nullable String packageName,
      Collection<ImportDeclaration> importDeclarations) {
    if (name == null) {
      return null;
    }

    List<String> nameParts = DOT_SPLITTER.splitToList(name);
    String firstNamePart = nameParts.get(0);
    for (ImportDeclaration importDeclaration : importDeclarations) {
      if (importDeclaration.isStatic()) {
        continue;
      }
      if (getLast(importDeclaration.name().parts()).equals(firstNamePart)) {
        return DOT_JOINER.join(
            concat(importDeclaration.name().parts(), nameParts.subList(1, nameParts.size())));
      }
    }
    return packageName.isEmpty() ? name : packageName + "." + name;
  }

  private static AbstractTypeDeclaration getContainingClass(ASTNode node) {
    while (node != null) {
      if (node instanceof AbstractTypeDeclaration) {
        return (AbstractTypeDeclaration) node;
      }
      node = node.getParent();
    }
    return null;
  }

  private static boolean isDescendantOfAnonymousClassDeclaration(ASTNode node) {
    while (node != null) {
      if (node instanceof AnonymousClassDeclaration) {
        return true;
      }
      node = node.getParent();
    }
    return false;
  }

  private static ImmutableSet<String> getMethodDeclarations(AbstractTypeDeclaration node) {
    return Streams.stream(Iterables.filter(node.bodyDeclarations(), MethodDeclaration.class))
        .map(m -> m.getName().getIdentifier())
        .collect(toImmutableSet());
  }

  /**
   * Parse a source file, attempting to resolve references in the AST. Valid Java files will usually
   * have errors in the CompilationUnit returned by this method, because classes, imports and
   * methods will be undefined (because we're only looking at a single file from a whole project).
   * Consequently, do not assume the returned object's getProblems() is an empty list. On the other
   * hand, {@link @parseSource} returns a CompilationUnit fit for syntax checking purposes.
   */
  private static CompilationUnit parseAndResolveSource(String source) {
    ASTParser parser = createCompilationUnitParser();
    parser.setSource(source.toCharArray());
    parser.setResolveBindings(true);
    parser.setBindingsRecovery(true);
    parser.setEnvironment(
        EMPTY_STRING_ARRAY,
        EMPTY_STRING_ARRAY,
        EMPTY_STRING_ARRAY,
        true /* includeRunningVMBootclasspath */);
    parser.setUnitName("dontCare");

    return (CompilationUnit) parser.createAST(null);
  }

  /** Parses a source file and returns its AST. */
  public static CompilationUnit parseSource(String source) {
    ASTParser parser = createCompilationUnitParser();
    parser.setSource(source.toCharArray());
    return (CompilationUnit) parser.createAST(null);
  }

  public static String getPackageOfJavaFile(String source) {
    return getPackageOfJavaFile(parseSource(source));
  }

  private static String getPackageOfJavaFile(CompilationUnit cu) {
    PackageDeclaration pkg = cu.getPackage();
    return pkg == null ? "" : pkg.getName().getFullyQualifiedName();
  }

  private static ASTParser createCompilationUnitParser() {
    ASTParser parser = ASTParser.newParser(AST.JLS8);
    Map options = JavaCore.getOptions();
    JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
    parser.setCompilerOptions(options);
    parser.setKind(ASTParser.K_COMPILATION_UNIT);
    return parser;
  }

  private static boolean isLowerCase(String s) {
    return s.equals(s.toLowerCase());
  }

  /**
   * Takes a tree representing a qualified type, e.g., Foo<Bla>.Bar<Asd>, and returns a string of
   * the same type, without any generics. That is, returns "Foo.Bar".
   */
  @VisibleForTesting
  static String extractTypeNameWithoutGenerics(QualifiedType type) {
    final StringBuilder sb = new StringBuilder();
    type.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(org.eclipse.jdt.core.dom.SimpleName node) {
            sb.append(node.getIdentifier());
            sb.append(".");
            return true;
          }

          @Override
          public boolean visit(ParameterizedType node) {
            node.getType().accept(this);
            return false;
          }
        });
    if (sb.length() > 0) {
      sb.deleteCharAt(sb.length() - 1);
    }
    return sb.toString();
  }

  /**
   * Example: ExternalClassA.ExternalAInnerClass => ExternalClassA
   * com.google.common.ExternalClassB.ExternalBInnerClass => com.google.common.ExternalClassB
   *
   * <p>Handles invalid classnames, and returns the empty string: "innerTemplate" => ""
   */
  @VisibleForTesting
  static String extractClassNameFromQualifiedName(String s) {
    List<String> parts = Lists.newArrayList(Splitter.on(".").split(s));
    for (int i = 0; i < parts.size(); i++) {
      String part = parts.get(i);
      if (isClassName.apply(part)) {
        return DOT_JOINER.join(parts.subList(0, i + 1));
      }
      if (isLowerCase(part)) {
        continue;
      }
      return "";
    }
    return "";
  }

  private static List<String> getCompilationMessages(String filename, String source) {
    CompilationUnit cu = parseSource(source);
    List<String> result = new ArrayList<>();
    for (Message message : cu.getMessages()) {
      result.add(
          String.format(
              "%s:%d: %s",
              filename, cu.getLineNumber(message.getStartPosition()), message.getMessage()));
    }
    return result;
  }

  private static boolean isJavaLangClass(String s) {
    // return true if the class is in java.lang.* but not if it is in a subpackage of java.lang
    if (isJavaLangClassPredicate.apply(s)) {
      return true;
    }
    try {
      Class.forName("java.lang." + s.replace('.', '$'), false, null);
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  /**
   * A class that represents a qualified symbol name, e.g., java.util.List or
   * com.google.common.truth.Truth.assertThat.
   */
  @AutoValue
  public abstract static class QualifiedName {
    public static final Pattern CLASS_NAME_PATTERN = Pattern.compile("^[A-Z][a-zA-Z0-9_$]*$");

    public static QualifiedName create(String value, Metadata metadata) {
      return new AutoValue_ReferencedClassesParser_QualifiedName(value, metadata);
    }

    public abstract String value();

    public abstract Metadata metadata();

    @Memoized
    public ImmutableList<String> parts() {
      return ImmutableList.copyOf(DOT_SPLITTER.split(value()));
    }

    private static int findTopLevelName(List<String> parts) {
      for (int i = 0; i < parts.size(); i++) {
        String part = parts.get(i);
        if (CLASS_NAME_PATTERN.matcher(part).matches()) {
          // if i == 0, 's' starts with a capitalized-camel name,
          // e.g., "MobileLocalDetailsJslayoutProto".
          return i > 0 ? i : -1;
        }
        if (isLowerCase(part)) {
          continue;
        }
        return -1;
      }
      return -1;
    }

    /**
     * "java.util.Map" => "java.util.Map" "java.util.Map.Entry" => "java.util.Map"
     * "org.mockito.Mockito.mock" => "org.mockito.Mockito"
     */
    public QualifiedName getTopLevelQualifiedName() {
      return create(DOT_JOINER.join(parts().subList(0, findTopLevelName(parts()) + 1)), metadata());
    }
  }

  /** A class representing a simple, unqualified class name, such as "AutoValue" or "List". */
  @AutoValue
  public abstract static class SimpleName {
    public abstract String value();

    public abstract Metadata metadata();

    public static SimpleName create(String value, Metadata metadata) {
      return new AutoValue_ReferencedClassesParser_SimpleName(value, metadata);
    }
  }

  @AutoValue
  public abstract static class Metadata {
    static final Metadata EMPTY = create(0, 0, false);

    /** Line where a simple/qualified name starts. */
    public abstract int line();

    /** Column where a simple/qualified name starts. */
    public abstract int column();

    public abstract boolean isMethod();

    public static Metadata create(int line, int column, boolean isMethod) {
      return new AutoValue_ReferencedClassesParser_Metadata(line, column, isMethod);
    }
  }

  @AutoValue
  public abstract static class ImportDeclaration {
    public abstract QualifiedName name();

    public abstract boolean isStatic();

    public static ImportDeclaration create(QualifiedName qname, boolean isStatic) {
      return new AutoValue_ReferencedClassesParser_ImportDeclaration(qname, isStatic);
    }
  }
}
