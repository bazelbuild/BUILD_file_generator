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
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Streams.stream;
import static com.google.devtools.build.bfg.BuildozerCommandCreator.computeBuildozerCommands;
import static com.google.devtools.build.bfg.BuildozerCommandCreator.getBuildFilesForBuildozer;
import static com.google.devtools.build.bfg.ClassGraphPreprocessor.preProcessClassGraph;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import com.google.re2j.Pattern;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import protos.com.google.devtools.build.bfg.Bfg.ParserOutput;

/** Entry point to the BUILD file generator. */
public class Bfg {

  @Option(name = "--buildozer", usage = "Path to the Buildozer binary")
  private String buildozerPath = "/usr/bin/buildozer";

  @Option(
    name = "--roots",
    usage =
        "Comma-separated list of paths where the source/test files reside, "
            + "relative to the WORKSPACE file."
  )
  private String contentRootPaths = "src/main/java/,src/test/java/";

  @Option(
    name = "--dry_run",
    usage =
        "Boolean flag indicating whether to execute the generated buildozer commands. "
            + "If true, commands will be printed but not executed."
            + "If false, they will be executed but not printed."
  )
  private boolean isDryRun = false;

  @Option(
    name = "--user_defined_mapping",
    usage = "Path to a file mapping class names to build rules"
  )
  private String userMapping = "";

  @Option(name = "--workspace", usage = "Path to the project's bazel WORKSPACE file")
  private String workspacePath = getCurrentWorkingDirectory();

  @Option(
    name = "--whitelist",
    usage =
        "Regular expression used to determine which classes to generate BUILD rules for. BUILD-file "
            + "generator will generate rules for any class-names whose fully-qualified name matches "
            + "this flag, where 'matches' means substring matching, e.g. 'bfg' matches "
            + "'com.bfg.Foo'."
  )
  private String whiteListRegex = "";

  @Option(
    name = "--blacklist",
    usage = "Regular expression used to determine which classes to ignore."
  )
  private String blackListRegex = "AutoValue_";

  @Option(
    name = "--external_resolvers",
    usage =
        "Comma-separated list of executables that will be used to resolve any classname --> BUILD "
            + "rules that couldn't be resolved by us. For example, looking up an external index."
  )
  private String externalResolvers = "";

  public static void main(String[] args) throws Exception {
    new Bfg().run(args);
  }

  private void run(String[] args) throws Exception {
    CmdLineParser cmdLineParser = new CmdLineParser(this);
    cmdLineParser.parseArgument(args);

    ParserOutput parserOutput = ParserOutput.parseFrom(System.in);
    if (parserOutput.getClassToClassMap().isEmpty()) {
      explainUsageErrorAndExit(cmdLineParser, "Expected nonempty class graph as input");
    }
    if (whiteListRegex.isEmpty()) {
      explainUsageErrorAndExit(cmdLineParser, "The --whitelist flag is required.");
    }
    Pattern whiteList = compilePattern(cmdLineParser, whiteListRegex);
    Pattern blackList = compilePattern(cmdLineParser, blackListRegex);

    ImmutableGraph<String> classGraph =
        preProcessClassGraph(
            protoMultimapToGraph(parserOutput.getClassToClassMap()), whiteList, blackList);

    ImmutableList<Path> contentRoots =
        stream(Splitter.on(',').split(contentRootPaths))
            .map(root -> Paths.get(root))
            .collect(toImmutableList());

    Path workspace = Paths.get(workspacePath);

    ImmutableList<String> userDefinedMapping;
    if (userMapping.isEmpty()) {
      userDefinedMapping = ImmutableList.of();
    } else {
      userDefinedMapping = ImmutableList.copyOf(Files.readAllLines(Paths.get(userMapping)));
    }

    ImmutableList.Builder<ClassToRuleResolver> resolvers =
        ImmutableList.<ClassToRuleResolver>builder()
            .add(new ProjectClassToRuleResolver(classGraph, whiteList, contentRoots, workspace))
            .add(new UserDefinedResolver(userDefinedMapping));
    for (String r : Splitter.on(',').omitEmptyStrings().split(externalResolvers)) {
      resolvers.add(new ExternalResolver(r));
    }

    ImmutableGraph<BuildRule> buildRuleGraph =
        new GraphProcessor(classGraph).createBuildRuleDAG(resolvers.build());

    executeBuildozerCommands(buildRuleGraph, workspace, isDryRun, buildozerPath);
  }

  private ImmutableGraph<String> protoMultimapToGraph(
      Map<String, protos.com.google.devtools.build.bfg.Bfg.Strings> m) {
    MutableGraph<String> result = GraphBuilder.directed().build();
    m.forEach(
        (u, deps) -> {
          for (String s : deps.getSList()) {
            result.putEdge(u, s);
          }
        });
    return ImmutableGraph.copyOf(result);
  }

  private static Pattern compilePattern(CmdLineParser cmdLineParser, String patternString) {
    try {
      return Pattern.compile(patternString);
    } catch (IllegalArgumentException e) {
      explainUsageErrorAndExit(cmdLineParser, String.format("Invalid regex: %s", e.getMessage()));
      return null;
    }
  }

  private static void explainUsageErrorAndExit(CmdLineParser cmdLineParser, String message) {
    System.err.println(message);
    cmdLineParser.printUsage(System.err);
    System.exit(1);
  }

  /**
   * Given a graph of build rules, some project specific and some external, this method constructs a
   * list of buildozer commands, that are then used to write various BUILD files to disk.
   */
  static void executeBuildozerCommands(
      ImmutableGraph<BuildRule> ruleDAG, Path workspace, boolean isDryRun, String buildozerBinary)
      throws IOException, InterruptedException {
    ImmutableList<String> commands = computeBuildozerCommands(ruleDAG);
    if (isDryRun) {
      commands.stream().forEach(command -> System.out.println(command));
      return;
    }

    for (Path buildFileDir : getBuildFilesForBuildozer(ruleDAG, workspace)) {
      generateBuildFileIfNecessary(buildFileDir);
    }
    File tempFile = File.createTempFile("bfgOutput", ".txt");
    try {
      Files.write(tempFile.toPath(), commands, StandardCharsets.US_ASCII);
      ProcessBuilder pb = new ProcessBuilder();
      pb.command(buildozerBinary, "-f", tempFile.toPath().toString(), "-k");
      pb.environment().clear();
      Process p = pb.start();
      if (p.waitFor() != 0) {
        System.err.println("Error from executing buildozer:");
        try (BufferedReader stderr =
            new BufferedReader(new InputStreamReader(p.getErrorStream(), UTF_8))) {
          String line;
          while ((line = stderr.readLine()) != null) {
            System.err.println(line);
          }
        }
      }
    } finally {
      Files.delete(tempFile.toPath());
    }
  }

  /**
   * Checks if a BUILD file exists at a given path. If it does not, then it generates the BUILD and
   * any necessary intermediary directories.
   */
  @VisibleForTesting
  static void generateBuildFileIfNecessary(Path buildFileDirectory)
      throws InterruptedException, IOException {
    checkState(Files.isDirectory(buildFileDirectory) || !Files.exists(buildFileDirectory));
    if (Files.exists(buildFileDirectory.resolve("BUILD"))) {
      return;
    }
    if (!Files.exists(buildFileDirectory)) {
      Files.createDirectories(buildFileDirectory);
    }
    Files.createFile(buildFileDirectory.resolve("BUILD"));
  }

  private static String getCurrentWorkingDirectory() {
    return Paths.get(checkNotNull(System.getProperty("user.dir"))).toString();
  }
}
