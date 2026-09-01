#!/usr/bin/env python3
"""Write composeResources values-<lang>/strings.xml from English + translations."""
from __future__ import annotations

import re
from pathlib import Path
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[1]
EN = ROOT / "shared/src/commonMain/composeResources/values/strings.xml"
OUT = ROOT / "shared/src/commonMain/composeResources"

# Official EU languages (except English, which is the default) plus zh, ja, ru.
LOCALES = [
    "bg", "hr", "cs", "da", "nl", "et", "fi", "fr", "de", "el", "hu", "ga",
    "it", "lv", "lt", "mt", "pl", "pt", "ro", "sk", "sl", "es", "sv",
    "zh", "ja", "ru",
]


def parse_en() -> dict[str, str]:
    text = EN.read_text(encoding="utf-8")
    return dict(re.findall(r'<string name="([^"]+)">(.*?)</string>', text, flags=re.S))


def write_locale(lang: str, values: dict[str, str], fallback: dict[str, str]) -> None:
    folder = OUT / f"values-{lang}"
    folder.mkdir(parents=True, exist_ok=True)
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    for key, en in fallback.items():
        raw = values.get(key, en)
        safe = escape(raw).replace("'", r"\'")
        lines.append(f'    <string name="{key}">{safe}</string>')
    lines.append("</resources>")
    (folder / "strings.xml").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    en = parse_en()
    for lang in LOCALES:
        table = TRANSLATIONS.get(lang, {})
        write_locale(lang, table, en)
        missing = [k for k in en if k not in table]
        print(f"{lang}: {len(en) - len(missing)}/{len(en)} translated")


# Translations are filled below. Unlisted keys fall back to English.
TRANSLATIONS: dict[str, dict[str, str]] = {}

if __name__ == "__main__":
    main()
