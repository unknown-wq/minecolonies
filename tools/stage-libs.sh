#!/usr/bin/env bash
# Put the jars Structurize compiles against into libs/structurize/26.3/libs/.
#
# Structurize's build.gradle takes Domum Ornamentum and BlockUI as plain
# `implementation files("libs/...")`, so those two jars have to exist before it will
# compile at all. They are not committed -- they are build outputs that get replaced
# the moment the two libraries are themselves ported.
#
# Two sources, in order of preference:
#
#   1. The ported builds, if they exist -- libs/{blockui,domum-ornamentum}/26.3/build/libs.
#      This is what the final integration pass must use.
#   2. Otherwise the 26.2 builds, unpacked out of the shipped mod jar, where they sit
#      as Jar-in-Jar under META-INF/jars/. Good enough to start porting Structurize
#      before the other two are done, and no /workspace needed.
#
# Prints which source each jar came from, because building Structurize against the
# 26.2 jars and calling it done is exactly the mistake this script must not hide.
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$REPO/libs/structurize/26.3/libs"
MODJAR="$REPO/dist/minecolonies-26.2-0.0.55.jar"
mkdir -p "$DEST"

stale=0

stage() { # <lib-dir> <jar-glob> <fallback-name>
	local libdir="$1" glob="$2" fallback="$3"
	local built
	built="$(ls -t "$REPO/libs/$libdir/26.3/build/libs/"$glob 2>/dev/null | grep -v sources | head -1 || true)"
	if [ -n "$built" ]; then
		cp "$built" "$DEST/"
		echo "  ported 26.3 build : $(basename "$built")"
		return
	fi
	if [ ! -f "$MODJAR" ]; then
		echo "  MISSING: no 26.3 build for $libdir and $MODJAR is absent" >&2
		exit 1
	fi
	unzip -o -q -j "$MODJAR" "META-INF/jars/$fallback" -d "$DEST"
	echo "  26.2 fallback     : $fallback   <-- not ported yet"
	stale=1
}

echo "Staging Structurize's compile dependencies into $DEST"
stage blockui 'blockui-*.jar' 'blockui-0.0.1.jar'
stage domum-ornamentum 'domum_ornamentum-*.jar' 'domum_ornamentum-26.2-1.0.0.jar'

if [ "$stale" -eq 1 ]; then
	echo
	echo "At least one dependency is still the 26.2 build. Structurize can be ported"
	echo "against it, but it is NOT verified until this script reports both jars as"
	echo "ported 26.3 builds and Structurize is rebuilt."
fi
