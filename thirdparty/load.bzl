def declare_maven(config):
    native.maven_jar(
        name = config["name"],
        artifact = config["artifact"],
        sha1 = config["sha1"],
        repository = config["repository"]
    )
    native.bind(
        name = config["bind"],
        actual = config["actual"]
    )
