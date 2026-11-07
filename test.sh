#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/classes build/test-classes
cp -R assets build/classes/
javac --release 17 -d build/classes src/main/java/laundromat/*.java
javac --release 17 -cp "build/classes:tools/junit-platform-console-standalone.jar" -d build/test-classes src/test/java/laundromat/*.java
java -jar tools/junit-platform-console-standalone.jar execute --class-path build/classes:build/test-classes --scan-class-path --disable-ansi-colors

