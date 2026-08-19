#!/usr/bin/env python3
"""Mechanical sanity check for a port-owned language file against en_us.json.

Checks, per file, that the translation
  * is valid UTF-8 JSON with no duplicate keys,
  * has exactly the key set of en_us.json,
  * lists its keys in the same order as en_us.json,
  * uses, per key, exactly the same multiset of format specifiers -- %s, %d,
    %1$s .. %9$s, the literal %% -- and the same number of newlines as en_us.

Argument order is not checked for indexed specifiers (%1$s and friends can be
moved about freely), but it cannot be checked for bare ones, so a bare-%s value
whose specifier count matches still has to be read by a human.

  usage: check-lang.py <lang-dir> [locale ...]      default locale: ru_ru
"""

import json
import re
import sys
from collections import Counter
from pathlib import Path

SPEC = re.compile(r"%(?:%|\d+\$[a-zA-Z]|[a-zA-Z])")


def load(path):
    """Parse a language file, refusing duplicate keys."""
    seen = []

    def pairs(items):
        seen.extend(k for k, _ in items)
        return dict(items)

    with path.open(encoding="utf-8") as handle:
        data = json.load(handle, object_pairs_hook=pairs)
    duplicates = [k for k, n in Counter(seen).items() if n > 1]
    return data, seen, duplicates


def check(lang_dir, locale):
    """Check one locale. Returns the number of problems found."""
    base_path = lang_dir / "en_us.json"
    other_path = lang_dir / f"{locale}.json"
    base, base_order, base_dupes = load(base_path)
    other, other_order, other_dupes = load(other_path)

    problems = []
    for name, dupes in ((base_path.name, base_dupes), (other_path.name, other_dupes)):
        problems.extend(f"{name}: duplicate key {k}" for k in dupes)

    missing = [k for k in base_order if k not in other]
    extra = [k for k in other_order if k not in base]
    problems.extend(f"missing key: {k}" for k in missing)
    problems.extend(f"key not in en_us: {k}" for k in extra)

    if not missing and not extra and base_order != other_order:
        first = next(a for a, b in zip(base_order, other_order) if a != b)
        problems.append(f"key order differs from en_us, first at: {first}")

    mismatched = 0
    for key in base_order:
        if key not in other:
            continue
        want, got = Counter(SPEC.findall(base[key])), Counter(SPEC.findall(other[key]))
        if want != got:
            mismatched += 1
            problems.append(f"{key}: placeholders {sorted(want.elements())} -> {sorted(got.elements())}")
        if base[key].count("\n") != other[key].count("\n"):
            problems.append(f"{key}: newline count differs")

    print(f"{other_path.name}: {len(other)} keys, en_us has {len(base)}; "
          f"{len(base_order) - len(missing) - mismatched} keys with matching placeholders")
    for problem in problems:
        print(f"  FAIL {problem}")
    print("  OK" if not problems else f"  {len(problems)} problem(s)")
    return len(problems)


def main(argv):
    """Entry point."""
    if len(argv) < 2:
        print(__doc__)
        return 2
    lang_dir = Path(argv[1])
    locales = argv[2:] or ["ru_ru"]
    return 1 if sum(check(lang_dir, locale) for locale in locales) else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
