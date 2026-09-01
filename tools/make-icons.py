#!/usr/bin/env python3
"""
Generate every icon the two apps need from one square source PNG.

    python3 tools/make-icons.py artwork/icon-source.png

Why the artwork gets scaled down for Android: an adaptive icon is a 108x108dp
canvas of which the launcher may mask away everything outside the central
66x66dp. A full-bleed design — like ours, where the book runs to the bottom edge
— loses its edges under a circular mask. So the foreground layer is the artwork
shrunk into the safe zone, sitting on a background layer painted the same navy
as the source. The seam is invisible because both are the same colour.

The desktop packages have no such constraint and use the artwork untouched.
"""

import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ANDROID_RES = ROOT / "androidApp/src/main/res"
DESKTOP_ICONS = ROOT / "desktopApp/icons"

# Legacy launcher bitmaps, still used by some launchers and by the store listing.
LEGACY_DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

# Adaptive layers are authored at 108dp; xxxhdpi means 4x that.
ADAPTIVE_SIZE = 432
# Fraction of the canvas the artwork may occupy: 66/108 is the guaranteed-safe
# zone. A little over it looks better and still survives every stock mask.
SAFE_ZONE = 0.72


def background_colour(image: Image.Image) -> tuple[int, int, int]:
    """The source is a flat field with art on top, so a corner pixel is the field."""
    return image.convert("RGB").getpixel((2, 2))


def write_adaptive_foreground(source: Image.Image) -> None:
    inner = int(ADAPTIVE_SIZE * SAFE_ZONE)
    artwork = source.resize((inner, inner), Image.LANCZOS)

    canvas = Image.new("RGBA", (ADAPTIVE_SIZE, ADAPTIVE_SIZE), (0, 0, 0, 0))
    offset = (ADAPTIVE_SIZE - inner) // 2
    canvas.paste(artwork, (offset, offset))

    target = ANDROID_RES / "drawable/ic_launcher_foreground.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(target)
    print(f"  {target.relative_to(ROOT)}  ({ADAPTIVE_SIZE}x{ADAPTIVE_SIZE})")

    # The hand-drawn vector placeholder would otherwise win over the PNG.
    vector = ANDROID_RES / "drawable/ic_launcher_foreground.xml"
    if vector.exists():
        vector.unlink()
        print(f"  removed {vector.relative_to(ROOT)} (replaced by the PNG)")


def write_background_colour(source: Image.Image) -> None:
    red, green, blue = background_colour(source)
    colours = ANDROID_RES / "values/colors.xml"
    colours.write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<resources>\n"
        f'    <color name="ic_launcher_background">#{red:02X}{green:02X}{blue:02X}</color>\n'
        "</resources>\n",
        encoding="utf-8",
    )
    print(f"  {colours.relative_to(ROOT)}  (#{red:02X}{green:02X}{blue:02X})")


def write_legacy_bitmaps(source: Image.Image) -> None:
    for density, size in LEGACY_DENSITIES.items():
        target = ANDROID_RES / f"mipmap-{density}/ic_launcher.png"
        target.parent.mkdir(parents=True, exist_ok=True)
        source.resize((size, size), Image.LANCZOS).save(target)
    print(f"  mipmap-*/ic_launcher.png  ({', '.join(map(str, LEGACY_DENSITIES.values()))}px)")


def write_desktop_icons(source: Image.Image) -> None:
    DESKTOP_ICONS.mkdir(parents=True, exist_ok=True)

    png = DESKTOP_ICONS / "icon.png"
    source.resize((512, 512), Image.LANCZOS).save(png)

    ico = DESKTOP_ICONS / "icon.ico"
    source.save(ico, sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])

    icns = DESKTOP_ICONS / "icon.png.512"
    if icns.exists():
        icns.unlink()

    print(f"  {png.relative_to(ROOT)}  (512x512, Linux)")
    print(f"  {ico.relative_to(ROOT)}  (multi-size, Windows)")


def write_store_icon(source: Image.Image) -> None:
    target = ROOT / "artwork/play-store-512.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    source.resize((512, 512), Image.LANCZOS).save(target)
    print(f"  {target.relative_to(ROOT)}  (512x512, Play Store listing)")


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2

    path = Path(sys.argv[1])
    if not path.is_file():
        print(f"not a file: {path}")
        return 1

    source = Image.open(path).convert("RGBA")
    if source.width != source.height:
        print(f"warning: source is {source.width}x{source.height}, not square — it will be squashed")
    if source.width < 512:
        print(f"warning: source is only {source.width}px; 1024px or more gives cleaner downscales")

    print(f"source: {path}  ({source.width}x{source.height})")
    write_adaptive_foreground(source)
    write_background_colour(source)
    write_legacy_bitmaps(source)
    write_desktop_icons(source)
    write_store_icon(source)
    print("done")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
