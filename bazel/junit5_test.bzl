load("@contrib_rules_jvm//java:defs.bzl", "JUNIT5_RUNTIME_DEPS")
load("@rules_java//java:defs.bzl", "java_test")

def junit5_test(
        name,
        runtime_deps = [],
        **kwargs):
    """Run junit5 tests using Bazel.

       This works the same way as java_test but uses the JUnit5 runner.
    """

    java_test(
        name = name,
        main_class = "com.github.bazel_contrib.contrib_rules_jvm.junit5.JUnit5Runner",
        runtime_deps = runtime_deps + JUNIT5_RUNTIME_DEPS,
        **kwargs
    )
