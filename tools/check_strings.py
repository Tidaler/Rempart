#!/usr/bin/env python3
"""Échappe les apostrophes non échappées dans les fichiers strings.xml (aapt les refuse).

Usage : python tools/check_strings.py [--fix]
Sans --fix : liste les problèmes et renvoie un code d'erreur s'il y en a.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
TAG = re.compile(r"(<(?:string|item)\b[^>]*>)(.*?)(</(?:string|item)>)", re.S)
BARE = re.compile(r"(?<!\\)'")


def main() -> int:
    fix = "--fix" in sys.argv
    problems = 0
    for path in ROOT.glob("*/src/main/res/values*/strings.xml"):
        text = path.read_text(encoding="utf-8")
        found = [m.group(2) for m in TAG.finditer(text) if BARE.search(m.group(2))]
        problems += len(found)
        for f in found:
            print(f"{path.relative_to(ROOT)}: {f[:70]}")
        if fix and found:
            text = TAG.sub(lambda m: m.group(1) + BARE.sub(r"\\'", m.group(2)) + m.group(3), text)
            path.write_text(text, encoding="utf-8")
    return 1 if problems and not fix else 0


if __name__ == "__main__":
    sys.exit(main())
