#!/usr/bin/env bash
# Compile and run SdlProbe against the same LWJGL 3.4.2 the 26.3 client uses.
#
#   DISPLAY=:99 tools/sdl-probe/run.sh                          window, the way 26.3 asks for it
#   DISPLAY=:99 tools/sdl-probe/run.sh nosrgb                   the same, minus the sRGB attribute
#   DISPLAY=:99 tools/sdl-probe/run.sh glx                      GLX fbconfig census
#   DISPLAY=:99 SDL_VIDEO_FORCE_EGL=1 tools/sdl-probe/run.sh    the EGL path
#
# Environment is passed straight through, which is the point: this is how you try
# SDL_VIDEODRIVER / GALLIUM_DRIVER / EGL_PLATFORM variants without starting the client and
# without taking the global Gradle lock. See docs/CLIENT-SCREENSHOTS.md §7.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="${SDL_PROBE_OUT:-/tmp/sdl-probe}"
CACHE=/root/.gradle/caches/modules-2/files-2.1/org.lwjgl

# The client resolves these through Loom; here they are picked straight out of the Gradle
# cache. Both the API jars and the -natives-linux jars are needed, so no filtering by name.
CP=""
for module in lwjgl lwjgl-opengl lwjgl-sdl; do
	for jar in "$CACHE/$module"/3.4.2/*/*.jar; do
		[ -f "$jar" ] || continue
		CP="$CP${CP:+:}$jar"
	done
done
if [ -z "$CP" ]; then
	echo "no LWJGL 3.4.2 jars under $CACHE -- run a Gradle build first so Loom populates the cache." >&2
	exit 1
fi

JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-25-openjdk-amd64}"
mkdir -p "$OUT"
"$JAVA_HOME/bin/javac" -nowarn -cp "$CP" -d "$OUT" "$HERE/SdlProbe.java"
exec "$JAVA_HOME/bin/java" -cp "$CP:$OUT" SdlProbe "$@"
