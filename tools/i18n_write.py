#!/usr/bin/env python3
"""Write a locale strings.xml. Usage: called with LANG and a dict from sibling packs."""
from __future__ import annotations

import json
import sys
from pathlib import Path
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "shared/src/commonMain/composeResources"
EN = OUT / "values/strings.xml"


def write_locale(lang: str, values: dict[str, str]) -> None:
    import re
    en_text = EN.read_text(encoding="utf-8")
    keys = re.findall(r'<string name="([^"]+)">', en_text)
    missing = [k for k in keys if k not in values]
    extra = [k for k in values if k not in keys]
    if missing or extra:
        raise SystemExit(f"{lang}: missing={missing[:8]} extra={extra[:8]} ({len(missing)} missing)")
    folder = OUT / f"values-{lang}"
    folder.mkdir(parents=True, exist_ok=True)
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    for key in keys:
        lines.append(f'    <string name="{key}">{escape(values[key])}</string>')
    lines.append("</resources>\n")
    (folder / "strings.xml").write_text("\n".join(lines), encoding="utf-8")
    print(f"{lang}: {len(keys)}/{len(keys)}")


if __name__ == "__main__":
    lang = sys.argv[1]
    values = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
    write_locale(lang, values)
