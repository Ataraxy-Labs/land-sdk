#!/bin/sh
# Compiles the SDK and its tests with javac alone and runs them against the fake land in fake. Java 17 or newer.
set -e
cd "$(dirname "$0")"
out=$(mktemp -d)
javac -d "$out" $(find src -name '*.java')
java -cp "$out" dev.useland.LandTest
