#!/usr/bin/env bash
# Serialise every Gradle invocation in this checkout behind one lock.
#
# Loom may not run twice at once: two invocations share ~/.gradle/caches/fabric-loom
# and race on the same Minecraft jars, and the loser corrupts the cache rather than
# waiting. The repository rule has always been "one Gradle at a time, through
# /home/user/mc-build.sh"; that script is not in this container, so this is it,
# living in the repository where every agent working this branch can find it.
#
#   tools/mc-build.sh <project-dir> <gradle-task> [gradle args...]
#
#   tools/mc-build.sh libs/blockui/26.3 compileJava
#   tools/mc-build.sh libs/structurize/26.3 build --no-daemon
#
# Waits for the lock rather than failing, so three agents can call it whenever they
# like and simply queue. LOCK_TIMEOUT (seconds, default 3600) caps the wait.
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOCK="${MC_BUILD_LOCK:-/tmp/mc-build.lock}"
TIMEOUT="${LOCK_TIMEOUT:-3600}"

if [ "$#" -lt 2 ]; then
	echo "usage: tools/mc-build.sh <project-dir> <gradle-task> [args...]" >&2
	exit 2
fi

PROJECT="$1"; shift
[ -d "$PROJECT" ] || PROJECT="$REPO/$PROJECT"
if [ ! -f "$PROJECT/build.gradle" ]; then
	echo "not a Gradle project: $PROJECT" >&2
	exit 2
fi

# Java 25 and Gradle 9.6.1 from gradle-dist/install.sh. Gradle 8.x on the image cannot
# run on Java 25 -- it dies parsing the build script with "Unsupported class file major
# version 69" -- so /opt/gradle-9.6.1 has to come first on PATH.
#
# JAVA_HOME is NOT trusted as inherited: this container exports Java 21, and honouring
# it gets `error: release version 25 not supported` out of compileJava. An inherited
# value is used only if it really is 25 or newer; otherwise it is replaced.
java_major() { # <java-home>
	local j="$1/bin/java"
	[ -x "$j" ] || return 1
	"$j" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1
}
if [ -z "${JAVA_HOME:-}" ] || [ "$(java_major "$JAVA_HOME" || echo 0)" -lt 25 ] 2>/dev/null; then
	JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
fi
if [ "$(java_major "$JAVA_HOME" || echo 0)" -lt 25 ] 2>/dev/null; then
	echo "[mc-build] no Java 25 found -- run ./gradle-dist/install.sh first." >&2
	exit 1
fi
export JAVA_HOME
export PATH="/opt/gradle-9.6.1/bin:$JAVA_HOME/bin:$PATH"

if [ ! -x /opt/gradle-9.6.1/bin/gradle ]; then
	echo "Gradle 9.6.1 is missing -- run ./gradle-dist/install.sh first." >&2
	exit 1
fi

echo "[mc-build] waiting for $LOCK ..." >&2
exec 9>"$LOCK"
if ! flock -w "$TIMEOUT" 9; then
	echo "[mc-build] could not take $LOCK within ${TIMEOUT}s; another build is still running." >&2
	exit 1
fi
echo "[mc-build] lock held: $PROJECT -> gradle $*" >&2

# --no-daemon on purpose: a daemon outlives the lock and would let the next caller's
# build share a JVM with state from someone else's project.
cd "$PROJECT"
gradle "$@" --no-daemon
