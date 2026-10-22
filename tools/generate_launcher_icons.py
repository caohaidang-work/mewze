#!/usr/bin/env python3
"""Regenerate the legacy (pre-Android 8) launcher icons and the Play Store icon
from app/src/main/res/drawable-nodpi/logo_app.png.

Usage (from the repo root):   pip install pillow && python3 tools/generate_launcher_icons.py

Android 8+ already uses logo_app.png directly through drawable/launcher_logo.xml,
so this script is only needed for old devices and the Play listing.
"""
from pathlib import Path
from PIL import Image, ImageDraw

RES = Path("app/src/main/res")
LOGO = Image.open(RES / "drawable-nodpi" / "logo_app.png").convert("RGBA")
BG = (255, 255, 255, 255)  # keep in sync with values/ic_launcher_background.xml
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def render(size: int, round_icon: bool) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), BG)
    inner = int(size * 0.66)
    logo = LOGO.copy()
    logo.thumbnail((inner, inner), Image.LANCZOS)
    canvas.alpha_composite(logo, ((size - logo.width) // 2, (size - logo.height) // 2))
    mask = Image.new("L", (size * 4, size * 4), 0)
    d = ImageDraw.Draw(mask)
    if round_icon:
        d.ellipse((0, 0, size * 4 - 1, size * 4 - 1), fill=255)
    else:
        d.rounded_rectangle((0, 0, size * 4 - 1, size * 4 - 1), radius=size * 4 * 0.22, fill=255)
    canvas.putalpha(mask.resize((size, size), Image.LANCZOS))
    return canvas


for density, px in SIZES.items():
    out = RES / f"mipmap-{density}"
    out.mkdir(exist_ok=True)
    render(px, False).save(out / "ic_launcher.webp", "WEBP", quality=95)
    render(px, True).save(out / "ic_launcher_round.webp", "WEBP", quality=95)
render(512, False).save(Path("app/src/main/ic_launcher-playstore.png"))
print("done")
