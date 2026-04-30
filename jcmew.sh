#!/usr/bin/env bash
# Wrapper for the jcme CLI using the uber-jar produced by `mvn package`.
# Run from the project root: ./jcmew.sh pages https://company.atlassian.net/...

set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Find the shaded jar under target/. Empty array when nothing matches.
shopt -s nullglob
jars=("$DIR"/target/jcme-*.jar)
shopt -u nullglob

JAR=""
for f in "${jars[@]}"; do
    case "$f" in
        *-sources.jar|*-javadoc.jar) continue ;;
    esac
    JAR="$f"
    break
done

if [[ -z "$JAR" ]]; then
    echo "Error: jcme jar not found under $DIR/target/." >&2
    echo "Run 'mvn package' first to build it." >&2
    exit 1
fi

exec java \
    --enable-native-access=ALL-UNNAMED \
    -Dfile.encoding=UTF-8 \
    -Dstdout.encoding=UTF-8 \
    -Dstderr.encoding=UTF-8 \
    -jar "$JAR" "$@"
