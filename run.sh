#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/classes
cp -R assets build/classes/
javac --release 17 -d build/classes src/main/java/laundromat/*.java
java -cp build/classes laundromat.Main "$@"
