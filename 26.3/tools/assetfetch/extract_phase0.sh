#!/usr/bin/env bash
# Copyright (C) 2026 the MineColonies Fabric port contributors.
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Phase 0 extraction. Pulls the four trees the asset-fetch work is derived from
# out of the retired port repository, into a scratch directory OUTSIDE any repo.
#
#   port-26.2/    the port's former assets/minecolonies tree  (4832 files)
#   upstream/     the upstream asset baseline                 (4822 files)
#   port-1.21.1/  the port's 1.21.1 snapshot of the same tree (4822 files)
#   generated/    datagen output at the retired head          (3792 files)
#
# NONE of these trees may be committed anywhere: everything under
# assets/minecolonies is All Rights Reserved upstream content. They exist only so
# the port's own edits can be expressed as patches, and as offline insurance for
# the owner.
#
# Usage: extract_phase0.sh [OLD_WORKING_COPY] [OUT_DIR]

set -euo pipefail

OLD="${1:-/home/user/minecolonies}"
OUT="${2:-/home/user/assetfetch-extraction}"

PORT_HEAD=ea4243c8f50bb0317554fa82bf813adcec5d4a92        # retired port head
UPSTREAM_COMMIT=325157eba3d971d75502cb58107a176db883c9be  # documented asset baseline
UPSTREAM_ASSETS=e7aa68bf8459544bc940af24de22bb0fff30bc55  # its assets/minecolonies tree

for object in "$PORT_HEAD" "$UPSTREAM_COMMIT" "$UPSTREAM_ASSETS"; do
    git -C "$OLD" cat-file -e "$object" || { echo "missing object $object" >&2; exit 1; }
done

mkdir -p "$OUT"/{port-26.2,port-1.21.1,upstream,generated,meta,insurance}

# The old working copy sits on an unrelated branch, so everything is read out of
# the commits/trees rather than off disk.
git -C "$OLD" archive "$PORT_HEAD" 26.2/src/main/resources/assets/minecolonies \
    | tar -x --strip-components=6 -C "$OUT/port-26.2"
git -C "$OLD" archive "$UPSTREAM_ASSETS" | tar -x -C "$OUT/upstream"
git -C "$OLD" archive "$PORT_HEAD" 1.21.1/src/main/resources/assets/minecolonies \
    | tar -x --strip-components=6 -C "$OUT/port-1.21.1"
git -C "$OLD" archive "$PORT_HEAD" 26.2/src/main/generated/assets \
    | tar -x --strip-components=5 -C "$OUT/generated"

# Insurance only (rev. 2 ships nothing derived from it): the citizen sound file
# names, so a future sounds.json could be rebuilt without the audio itself.
( cd "$OUT/upstream/sounds/mob/citizen" && find . -type f | sed 's|^\./||' | sort ) \
    > "$OUT/meta/citizen-sound-filenames.txt"

for tree in port-26.2 port-1.21.1 upstream generated; do
    printf '%-14s %s files\n' "$tree" "$(find "$OUT/$tree" -type f | wc -l)"
done
