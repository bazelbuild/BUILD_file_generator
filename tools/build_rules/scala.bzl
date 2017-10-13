# scala_import by github.com/ittaiz
# https://github.com/bazelbuild/rules_scala/pull/278#issuecomment-329756434
# https://gist.github.com/ittaiz/f83d145c14414a7427ab9c393a3d57e6

def _scala_import_impl(ctx):
    code_jars = _filter_out_non_code_jars(ctx.attr.jar.files)
    code_jars_depset = depset(code_jars)
    return struct(
        scala = struct(
          outputs = struct(
            ijar = None,
            class_jar = code_jars[0],
          ),
        ),
        providers = [
          java_common.create_provider(
            compile_time_jars = code_jars_depset,
            runtime_jars = code_jars_depset + _collect(ctx.attr.runtime_deps),
          )
        ],
    )

def _filter_out_non_code_jars(files):
    return [file for file in files if not _is_source_jar(file)]

def _is_source_jar(file):
    return file.basename.endswith("-sources.jar")

def _collect(runtime_deps):
    transitive_runtime_jars = depset()
    for runtime_dep in runtime_deps:
        transitive_runtime_jars += runtime_dep[java_common.provider].transitive_runtime_jars
    return transitive_runtime_jars


scala_import = rule(
  implementation=_scala_import_impl,
  attrs={
      "jar": attr.label(),
      "runtime_deps": attr.label_list()
      },
)
