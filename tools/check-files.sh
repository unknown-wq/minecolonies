#!/usr/bin/env bash
# Answer one question: are MY files free of compile errors?
#
#   tools/check-files.sh <project-dir> <path-fragment> [more fragments...]
#
#   tools/check-files.sh 26.3 core/entity/ api/entity/
#   tools/check-files.sh 26.3 BlockStateStorage.java BuildingUtils.java
#
# Why this exists: while several agents port one mod in parallel, the project as a
# whole does not compile -- by design, until the last zone is done. `gradle build`
# therefore always fails and tells you nothing about your own work, and it takes the
# global Loom lock away from everyone else for the privilege.
#
# This runs javac directly instead. It needs no lock, finishes in seconds, and works
# perfectly well on a red project: javac reports errors per file, so filtering its
# output to your own paths gives a clean yes/no.
#
# Exit 0 means no errors in files matching your fragments. Exit 1 lists them.
#
# The merged Minecraft jar with the AccessWidener already applied must come FIRST on the
# classpath, or javac invents a hundred false "has private access" errors on members the
# widener opened. Loom writes that jar under the project's loom-cache, one per AW state;
# the newest is the right one. ~/.gradle/caches/fabric-loom/<version>/minecraft-*.jar is
# the jar WITHOUT the widener and must not be used.
set -uo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVAC=/usr/lib/jvm/java-25-openjdk-amd64/bin/javac

if [ "$#" -lt 2 ]; then
	echo "usage: tools/check-files.sh <project-dir> <path-fragment> [more...]" >&2
	exit 2
fi

PROJECT="$1"; shift
[ -d "$PROJECT" ] || PROJECT="$REPO/$PROJECT"
SRC="$PROJECT/src/main/java"
[ -d "$SRC" ] || { echo "no source root: $SRC" >&2; exit 2; }

MC="$(ls -t "$PROJECT"/.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*/*/*.jar 2>/dev/null | grep -v sources | head -1)"
if [ -z "$MC" ]; then
	echo "No Loom merged jar under $PROJECT/.gradle/loom-cache." >&2
	echo "Run 'tools/mc-build.sh $1 compileJava' once first so Loom produces it." >&2
	exit 2
fi

# The Gradle cache holds every version ever resolved here, and this repository has built
# against both 26.2 and 26.3 -- so dozens of fabric-api submodules sit in it twice. A bare
# `find` picks whichever the filesystem hands over first, and when the 26.2 copy wins the
# tool reports errors that do not exist: it flagged an already-correct `createRecipeProvider`
# because it was checking against the old fabric-api. False alarms are worse than no tool,
# so duplicates are resolved deterministically -- highest version per group:artifact.
#
# Layout is .../modules-2/files-2.1/<group>/<artifact>/<version>/<hash>/<file>.jar
dedup_libs() {
	find "$HOME/.gradle/caches/modules-2" -name '*.jar' 2>/dev/null | grep -v sources \
	| awk -F'/files-2.1/' 'NF==2 {
		n = split($2, p, "/")
		if (n >= 4) print p[1] "/" p[2] "\t" p[3] "\t" $0
		else print $2 "\t0\t" $0
	  }' \
	| sort -t$'\t' -k1,1 -k2,2Vr \
	| awk -F'\t' '!seen[$1]++ { print $3 }'
}

LIBS="$( { dedup_libs
	find "$HOME/.gradle/caches/fabric-loom" -name '*.jar' 2>/dev/null \
		| grep -v sources | grep -v '/minecraft-merged'
  } | tr '\n' ':')"
CP="$MC:$LIBS"
for j in "$REPO"/libs/*/26.3/build/libs/*.jar; do
	[ -f "$j" ] && CP="$CP:$j"
done

# Simple Planes is the one integration build.gradle decides for itself: the aircraft package
# compiles when the jar named by simpleplanes_jar is on this machine and is dropped when it is
# not. The jar is a `compileOnly files(...)` local path, so unlike every other dependency it
# never lands in the Gradle module cache and the sweep above cannot find it -- it has to be put
# on the classpath by name. Missing that turned the whole aircraft package into 40-odd bogus
# "cannot find symbol" errors on a machine where it actually builds.
sp_jar=""
for props in "$PROJECT/gradle.properties" "$REPO/gradle.properties"; do
	[ -f "$props" ] || continue
	v="$(sed -n 's/^[[:space:]]*simpleplanes_jar[[:space:]]*=[[:space:]]*//p' "$props" | head -1)"
	[ -n "$v" ] && { sp_jar="$v"; break; }
done
if [ -n "$sp_jar" ] && [ -f "$sp_jar" ]; then
	CP="$CP:$sp_jar"
else
	[ -n "$sp_jar" ] && echo "note: simpleplanes_jar=$sp_jar is not on this machine; aircraft package skipped, as the build skips it too" >&2
	sp_jar=""
fi

# The build does not compile every .java under src/main/java, and this tool must not either.
# `find` alone globs the parked optional integrations back in, so a whole-mod run used to
# report hundreds of errors in JEI and JourneyMap code that Gradle never looks at -- a number
# nobody can act on, sitting in the same list as the real ones. The two exclusion rules below
# mirror 26.3/build.gradle exactly (lines 135-154); change them together or the tool starts
# lying again.
EXCLUDES=()
PARKED="$PROJECT/optional-integrations.txt"
if [ -f "$PARKED" ]; then
	while IFS= read -r line; do
		line="${line#"${line%%[![:space:]]*}"}"   # ltrim, matching Groovy's it.trim()
		[ -z "$line" ] && continue
		case "$line" in \#*) continue ;; esac
		EXCLUDES+=("$line")
	done < "$PARKED"
fi

if [ -z "$sp_jar" ]; then
	EXCLUDES+=('com/minecolonies/core/compatibility/simpleplanes/**')
fi

is_excluded() {
	local rel="$1" pat
	for pat in ${EXCLUDES+"${EXCLUDES[@]}"}; do
		# Unquoted on purpose: bash glob matching, where * spans '/' too, so a trailing
		# '**' behaves like Gradle's and a wildcard-free line is an exact path match.
		[[ "$rel" == $pat ]] && return 0
	done
	return 1
}

# Collect the files to compile: everything matching the fragments, minus the parked ones.
FILES=()
skipped=0
for frag in "$@"; do
	while IFS= read -r f; do
		if is_excluded "${f#"$SRC/"}"; then
			skipped=$(( skipped + 1 ))
			continue
		fi
		FILES+=("$f")
	done < <(find "$SRC" -name '*.java' -path "*$frag*")
done
if [ "${#FILES[@]}" -eq 0 ]; then
	echo "no files matched: $*" >&2
	[ "$skipped" -gt 0 ] && echo "($skipped matched but are parked in optional-integrations.txt)" >&2
	exit 2
fi

OUT="$(mktemp)"
trap 'rm -f "$OUT"' EXIT

# -sourcepath lets javac resolve the rest of the mod from source. It will therefore also
# report errors in OTHER people's unported files that yours happen to reference -- that is
# expected and is exactly what gets filtered out below. Do not try to make this number
# zero; it is not your number.
"$JAVAC" --release 25 -nowarn -Xmaxerrs 100000 -Xmaxwarns 0 \
	-cp "$CP" -sourcepath "$SRC" -d "$(mktemp -d)" "${FILES[@]}" > "$OUT" 2>&1

total=$(grep -c ' error:' "$OUT" || true)

pattern="$(printf '%s\n' "$@" | paste -sd'|' -)"
mine="$(grep ' error:' "$OUT" | grep -E "$pattern" || true)"

echo "checked ${#FILES[@]} file(s) matching: $*"
[ "$skipped" -gt 0 ] && echo "skipped $skipped parked file(s) the build excludes too"
echo "errors pulled in from other zones (not yours, ignore): $(( total - $(printf '%s' "$mine" | grep -c . || true) ))"
echo

if [ -z "$mine" ]; then
	echo "CLEAN -- no errors in your files."
	exit 0
fi

echo "ERRORS IN YOUR FILES:"
printf '%s\n' "$mine"
exit 1
