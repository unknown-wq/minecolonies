#!/usr/bin/env bash
# Boot a dedicated Fabric server for 26.3-snapshot-9 with the given mod jars and say
# whether it came up clean.
#
#   tools/run-server.sh <mod.jar> [more.jar ...]
#   tools/run-server.sh libs/blockui/26.3/build/libs/blockui-0.0.1.jar
#
# Every library agent runs the same harness so the results are comparable. Compiling is
# not the test: a library can build green and still take the server down on registry
# freeze, mixin apply or entrypoint init, and that is precisely what has to be caught
# before the mod is ported on top of it.
#
# Exit 0 only if the server reaches "Done (Ns)! For help, type help" and then stops on
# request. Anything else -- crash, exception during startup, timeout -- is a failure,
# and the tail of the log is printed.
#
# The run directory is scratch, outside the repository, one per invocation set:
#   ${MC_SERVER_DIR:-/tmp/mc-server-26.3}
# Delete it to start from a fresh world.
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN="${MC_SERVER_DIR:-/tmp/mc-server-26.3}"
MCVER=26.3-snapshot-9
LOADER=0.19.3
INSTALLER=1.1.2
FABRIC_API="$REPO/.cache/fabric-api-0.158.0+26.3.jar"
BOOT_TIMEOUT="${BOOT_TIMEOUT:-300}"

if [ "$#" -lt 1 ]; then
	echo "usage: tools/run-server.sh <mod.jar> [more.jar ...]" >&2
	exit 2
fi

for j in "$@"; do
	[ -f "$j" ] || { echo "no such jar: $j" >&2; exit 2; }
done

java_home=/usr/lib/jvm/java-25-openjdk-amd64
[ -x "$java_home/bin/java" ] || { echo "Java 25 missing -- run ./gradle-dist/install.sh" >&2; exit 1; }
JAVA="$java_home/bin/java"

mkdir -p "$RUN/mods" "$REPO/.cache"

# --- one-time downloads, cached ------------------------------------------------------
if [ ! -f "$RUN/fabric-server-launch.jar" ]; then
	echo "[server] fetching Fabric server launcher $LOADER for $MCVER"
	curl -sSL -o "$RUN/fabric-server-launch.jar" \
		"https://meta.fabricmc.net/v2/versions/loader/$MCVER/$LOADER/$INSTALLER/server/jar"
fi
if [ ! -f "$FABRIC_API" ]; then
	echo "[server] fetching fabric-api"
	curl -sSL -o "$FABRIC_API" \
		"https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.158.0%2B26.3/fabric-api-0.158.0%2B26.3.jar"
fi

# --- mods ----------------------------------------------------------------------------
# Rebuilt from scratch every run: a stale jar from the previous attempt loading next to
# the new one is a confusing way to lose an afternoon.
rm -f "$RUN"/mods/*.jar
cp "$FABRIC_API" "$RUN/mods/"
for j in "$@"; do
	cp "$j" "$RUN/mods/"
	echo "[server] mod: $(basename "$j")"
done

# The launcher downloads the vanilla server itself, but the jar is already on this box.
mkdir -p "$RUN/.fabric/server"
[ -f "/opt/mc-$MCVER/server.jar" ] && cp -n "/opt/mc-$MCVER/server.jar" "$RUN/.fabric/server/$MCVER-server.jar" 2>/dev/null || true

# Mojang's EULA. This is the same acceptance a human makes to run any server; the PR
# template already counts a server boot as evidence, so the harness makes it explicit
# rather than hiding it.
echo "eula=true" > "$RUN/eula.txt"

# The port is settable because two of these can be running at once. Agents working in
# parallel each boot their own server, and the second one used to die on "FAILED TO BIND TO
# PORT / Address already in use" -- which reads exactly like a mod defect in the log and is
# not one. Pair MC_SERVER_PORT with MC_SERVER_DIR so the runs do not share a directory either.
PORT="${MC_SERVER_PORT:-25565}"

cat > "$RUN/server.properties" <<PROPS
level-type=minecraft\\:flat
level-name=boottest
online-mode=false
spawn-protection=0
max-players=1
view-distance=4
sync-chunk-writes=false
server-port=$PORT
PROPS

# --- boot ----------------------------------------------------------------------------
LOG="$RUN/boot.log"
rm -f "$LOG"
echo "[server] booting (timeout ${BOOT_TIMEOUT}s) ..."

# The server writes eula.txt, server.properties, libraries/, versions/ and the world
# relative to its working directory, so it MUST run inside $RUN. Launching it from the
# repository root drops 46 MB of libraries/ and a world into the checkout -- which is
# exactly what the first version of this script did.
#
# `stop` on stdin: the server shuts down cleanly as soon as it accepts commands, so a
# healthy run ends by itself instead of being killed at the timeout.
#
# The feeder must be bounded too. Its first version looped `until grep -q 'Done ('`
# with no exit condition, so a server that died during startup -- exactly the case this
# harness exists to catch -- left it spinning forever: `timeout` covers java, not the
# feeder, and the pipeline waits for both. A failing run hung for ten minutes on a
# server that had been dead for eight seconds. It now also stops when java is gone or
# the deadline passes.
set +e
( cd "$RUN" && \
  ( sleep 1
    waited=0
    while ! grep -q 'Done (' "$LOG" 2>/dev/null; do
        # java is the pipeline's other end; if it is gone there is nobody to send to.
        pgrep -f 'fabric-server-launch.jar' >/dev/null 2>&1 || exit 0
        [ "$waited" -ge "$BOOT_TIMEOUT" ] && exit 0
        sleep 2
        waited=$((waited + 2))
    done
    echo stop ) \
	| timeout "$BOOT_TIMEOUT" "$JAVA" -Xmx2G -jar "$RUN/fabric-server-launch.jar" nogui \
	> "$LOG" 2>&1 )
rc=$?
set -e
done_line="$(grep -m1 'Done (' "$LOG" || true)"

# Fatal means fatal. The first version of this check failed the run on any line matching
# Exception|ERROR], and a first boot -- the one that generates the world -- prints such
# lines while succeeding perfectly well; it failed a healthy server. A harness that cries
# wolf gets ignored, so the fatal list is now only what actually stops a server or keeps
# a mod from loading.
FATAL_RE='Crash report|Mixin apply.*[Ff]ailed|Failed to start|Mod resolution|ExceptionInInitializerError|Failed to load mod|requires .* which is missing|Incompatible mod'
fatal="$(grep -m1 -E "$FATAL_RE" "$LOG" || true)"
errors="$(grep -cE '/ERROR\]|/FATAL\]' "$LOG" || true)"

echo
if [ -n "$done_line" ]; then
	echo "[server] BOOT OK: $done_line"
else
	echo "[server] BOOT FAILED (exit $rc) -- no 'Done (' in $LOG"
fi

# Non-fatal error lines are reported, never hidden, but do not by themselves fail the
# run -- read them and decide. If they come from your library they belong in the
# "деградации" or "не проверено" part of your report.
if [ "${errors:-0}" -gt 0 ]; then
	echo "[server] $errors ERROR/FATAL log lines (not fatal on their own -- read them):"
	grep -E '/ERROR\]|/FATAL\]' "$LOG" | head -10 | sed 's/^/    /'
fi

if [ -z "$done_line" ] || [ -n "$fatal" ] || [ "$rc" -ne 0 ]; then
	[ -n "$fatal" ] && echo "[server] FATAL: $fatal"
	echo
	echo "----- last 60 lines of $LOG -----"
	tail -60 "$LOG"
	exit 1
fi

echo "[server] clean shutdown, log: $LOG"
