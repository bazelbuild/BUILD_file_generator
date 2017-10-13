load('//tools/build_rules:scala.bzl', 'scala_import')

def _scala_maven_jar_impl(repository_ctx):
    repository_ctx.file(
        'BUILD.bazel',
        content = "load('@//tools/build_rules:scala.bzl', 'scala_import')\nscala_import(name='scala_jar', jar='%s', visibility=['//visibility:public'])" % repository_ctx.attr.jar,
        executable = False,
    )

scala_maven_jar = repository_rule(
    implementation = _scala_maven_jar_impl,
    attrs = {
        "jar": attr.label(),
    },
    local = True,
)

def declare_maven(hash):
    '''Used in WORKSPACE to reify the dependencies calculated by bazel-deps into importable targets.'''

    # Here be dragons.
    #
    # The maven_jar repository rule from Bazel downloads the given jar and converts it to a bazel repository by
    # generating a BUILD file.  That BUILD file defines two targets, which are bound under the "@<name>//" repository: -
    # @<name>//jar -- this is a java_import target that loads the jar for Bazel's use - @<name>//jar:file -- this is a
    # filegroup referencing the jar file
    #
    # For java libraries, we bind the //jar target to the label "//external:<bind>" (with <bind> from the hash); the
    # java_library targets in the third_party/jvm/ build files then reference those bound labels.  Easy peasy.
    #
    # Scala libraries are a bit different, because the generated java_import target doesn't work for scala jars --
    # specifically, it doesn't expose scala macros from the external JAR, so compilation fails if we bind the //jar
    # target to the //external: label.  bazel-deps handles this by binding the //jar:file target from all scala
    # libraries to the //external: label, automatically -- hash['actual'] is //jar:file for scala libraries and //jar
    # for java ones.  scala_library correctly handles the dependency being either a java_import or a filegroup target,
    # making macros work.  bazel-deps + rules_scala make this all work out of the box.
    #
    # Unfortunately, scala_library's handling of jars-as-filegroups is not mirrored in intellij, so intellij is not able
    # to locate any classes provided by scala libraries.  That's a big problem.  The intellij plugin needs all
    # dependencies to be a java_import-like target, not a filegroup, so it can correctly locate classes inside the jars.
    # That's where this method's fanciness comes in: for scala libraries, instead of binding the //jar:file target to
    # the //external label, we instead take the //jar:file target and pass it to scala_import, and bind the imported
    # result to the //external:label.  In other words, we create the equivalent of maven_jar's //jar target, but with
    # the scala_import rule, not the java_import one.  Intellij correctly processes dependencies from the
    # scala_import'ed jar, and scalac handles them correctly to at build time.
    #
    # We create this scala_import target by way of the scala_maven_jar repository rule above, generating a BUILD file
    # that declares a target "@<name>__imported//:scala_jar"; that target uses scala_import.  Like maven_jar, it creates
    # a new repository "@<name>__imported", referencing the "@<name>//jar:file" target from maven_jar.  By then binding
    # that :scala_jar target to the //external label, no changes to the generated BUILD files in third_party/ are
    # required -- binding to the //external label abstracts away the different kinds of targets that are used for
    # different kinds of libraries.
    #
    # See https://github.com/bazelbuild/rules_scala/pull/278 for lots of discussion and the source of scala_import.  A
    # future release of rules_scala or bazel-deps will hopefully include scala_import, and may remove the need for
    # scala_maven_jar entirely.

    native.maven_jar(
        name = hash["name"],
        artifact = hash["artifact"],
        sha1 = hash["sha1"],
        repository = hash["repository"]
    )
    if hash['lang'] == 'scala':
        imported_name = hash['name'] + '__imported'
        scala_maven_jar(
            name = imported_name,
            jar = hash['actual'],
        )
        native.bind(
            name = hash["bind"],
            actual = '@%s//:scala_jar' % imported_name,
        )
    else:
        native.bind(
            name = hash["bind"],
            actual = hash["actual"]
        )
