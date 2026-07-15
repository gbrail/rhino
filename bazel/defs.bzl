load("@contrib_rules_jvm//java:defs.bzl", _JUNIT5_DEPS = "JUNIT5_DEPS")
load("//bazel:errorprone.bzl", _LIBRARY_OPTS = "LIBRARY_OPTS", _TEST_OPTS = "TEST_OPTS")
load("//bazel:java_test_suite.bzl", _java_test_suite = "java_test_suite")
load("//bazel:junit5_test.bzl", _junit5_test = "junit5_test")
load("//bazel:sharded_test.bzl", _sharded_test = "sharded_parameterized_test")

java_test_suite = _java_test_suite
junit5_test = _junit5_test
LIBRARY_OPTS = _LIBRARY_OPTS
TEST_OPTS = _TEST_OPTS
JUNIT5_DEPS = _JUNIT5_DEPS
sharded_parameterized_test = _sharded_test
