load('//tools/bazel_defs:scala.bzl', 'scala_import')

def _scala_maven_jar_impl(repository_ctx):
    repository_ctx.file(
        'BUILD.bazel',
        content = "load('@//tools/bazel_defs:scala.bzl', 'scala_import')\nscala_import(name='scala_jar', jar='%s', visibility=['//visibility:public'])" % repository_ctx.attr.jar,
        executable = False,
    )

scala_maven_jar = repository_rule(
    implementation = _scala_maven_jar_impl,
    attrs = {
        "jar": attr.label(),
    },
    local = True,
)