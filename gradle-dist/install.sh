#!/usr/bin/env bash
# Reassemble and install the vendored Gradle 9.6.1 distribution, plus the two
# apt packages the build actually needs: unrar (to unpack this distribution)
# and openjdk-25-jdk (Minecraft 26.2 targets Java 25).
#
# Gradle is vendored because the Gradle wrapper cannot download through this
# environment's egress proxy (GitHub release assets return 403).
#
# Skip the apt step with:  SKIP_APT=1 ./install.sh
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEST="${GRADLE_DEST:-/opt/gradle-9.6.1}"
JDK_PKG="openjdk-25-jdk"
JAVA_HOME_DEFAULT="/usr/lib/jvm/java-25-openjdk-amd64"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# Running as root in a container is the common case here, where sudo is often
# absent; fall back to running the command directly.
if [ "$(id -u)" -eq 0 ]; then
  SUDO=""
elif command -v sudo >/dev/null 2>&1; then
  SUDO="sudo"
else
  echo "Not root and sudo is unavailable; re-run as root." >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# Dependencies: unrar, unzip, Java 25
# ---------------------------------------------------------------------------
need_pkgs=()
command -v unrar >/dev/null 2>&1 || need_pkgs+=(unrar)
command -v unzip >/dev/null 2>&1 || need_pkgs+=(unzip)

# Any JDK >= 25 on PATH or at the default location is good enough.
have_java25=0
for candidate in "$JAVA_HOME_DEFAULT/bin/java" "$(command -v java || true)"; do
  [ -x "$candidate" ] || continue
  # "openjdk version \"25.0.3\"" -> 25
  major="$("$candidate" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)"
  if [ -n "$major" ] && [ "$major" -ge 25 ] 2>/dev/null; then
    have_java25=1
    break
  fi
done
[ "$have_java25" -eq 1 ] || need_pkgs+=("$JDK_PKG")

if [ "${#need_pkgs[@]}" -gt 0 ]; then
  if [ "${SKIP_APT:-0}" = "1" ]; then
    echo "Missing: ${need_pkgs[*]} (SKIP_APT=1 set, install them yourself)" >&2
    exit 1
  fi
  echo "Installing: ${need_pkgs[*]}"
  # apt-get update first: a stale package index makes the openjdk-25 fetch fail
  # with a 404 on archive.ubuntu.com even though the version is listed.
  $SUDO apt-get update
  DEBIAN_FRONTEND=noninteractive $SUDO apt-get install -y "${need_pkgs[@]}"
fi

# unrar-nonfree registers itself under the plain `unrar` name via alternatives.
command -v unrar >/dev/null 2>&1 || { echo "unrar still not on PATH after install" >&2; exit 1; }

# ---------------------------------------------------------------------------
# Gradle
# ---------------------------------------------------------------------------
if [ -d "$DEST" ]; then
  echo "Already installed: $DEST"
else
  # unrar needs volumes named *.part1.rar, *.part2.rar, ... side by side.
  cp "$DIR"/gradle-9.6.1-bin.part*.rar "$WORK"/
  ( cd "$WORK" && unrar x -o+ gradle-9.6.1-bin.part1.rar >/dev/null )

  ZIP="$WORK/gradle-9.6.1-bin.zip"
  [ -f "$ZIP" ] || { echo "extraction failed: $ZIP missing" >&2; exit 1; }

  $SUDO unzip -q "$ZIP" -d "$(dirname "$DEST")"
fi

# Resolve the JAVA_HOME to advertise: prefer the 25 install we just ensured.
JAVA_HOME_OUT="$JAVA_HOME_DEFAULT"
[ -x "$JAVA_HOME_OUT/bin/java" ] || JAVA_HOME_OUT="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"

echo
echo "Gradle installed at: $DEST"
echo "Java 25 at:          $JAVA_HOME_OUT"
echo
echo "Use it with:"
echo "  export JAVA_HOME=$JAVA_HOME_OUT"
echo "  export PATH=$DEST/bin:\$JAVA_HOME/bin:\$PATH"
echo "  gradle build --no-daemon      # not ./gradlew, its download is blocked"
