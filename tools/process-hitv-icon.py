#!/usr/bin/env python3
"""Build complete Hi TV launcher icons from the supplied artwork.

Keeps the full opaque squircle (TV + Hi TV lettering). Drops only the
transparent page and the caption under the icon. No color-key punch-outs.
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SRC = Path(
    r"C:\Users\Administrator\.cursor\projects\c-Users-Administrator-Desktop-HiTV"
    r"\assets\HITV-09a42127-028c-43d0-99a5-0993370e5616.png"
)
BRAND = ROOT / "docs" / "brand"
APP_RES = ROOT / "app" / "src" / "main" / "res"
LITE_RES = ROOT / "lite" / "src" / "main" / "res"
LEGACY = (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192))
DARK = (7, 11, 20, 255)


def is_opaque(a: int) -> bool:
    return a > 200


def is_dark_fill(r: int, g: int, b: int, a: int) -> bool:
    return is_opaque(a) and (r + g + b) / 3.0 < 70 and max(r, g, b) - min(r, g, b) < 55


def crop_squircle(im: Image.Image) -> Image.Image:
    """Tight crop of the dark rounded icon only (no caption)."""
    w, h = im.size
    px = im.load()

    # Rows that belong to the squircle: many dark fill pixels.
    dark_rows = []
    for y in range(h):
        dark = sum(1 for x in range(w) if is_dark_fill(*px[x, y]))
        if dark >= 80:
            dark_rows.append(y)

    if not dark_rows:
        raise SystemExit("no dark squircle rows found")

    # Longest contiguous run = the icon body (caption rows are short/sparse).
    runs: list[tuple[int, int]] = []
    start = prev = dark_rows[0]
    for y in dark_rows[1:]:
        if y == prev + 1:
            prev = y
        else:
            runs.append((start, prev))
            start = prev = y
    runs.append((start, prev))
    top, bottom = max(runs, key=lambda r: r[1] - r[0])

    left, right = w, 0
    for y in range(top, bottom + 1):
        for x in range(w):
            if is_opaque(px[x, y][3]):
                left = min(left, x)
                right = max(right, x)

    pad = 2
    left = max(0, left - pad)
    right = min(w - 1, right + pad)
    top = max(0, top - pad)
    bottom = min(h - 1, bottom + pad)
    crop = im.crop((left, top, right + 1, bottom + 1)).convert("RGBA")
    print(f"squircle crop {crop.size} rows {top}-{bottom} cols {left}-{right} runs={runs}")

    # Opaque dark plate: transparent page pixels become DARK so AA edges stay.
    out = Image.new("RGBA", crop.size, DARK)
    cp, op = crop.load(), out.load()
    cw, ch = crop.size
    for y in range(ch):
        for x in range(cw):
            r, g, b, a = cp[x, y]
            if not is_opaque(a):
                op[x, y] = DARK
            else:
                # Premultiplied-ish soft edges: blend onto DARK by alpha.
                if a >= 250:
                    op[x, y] = (r, g, b, 255)
                else:
                    t = a / 255.0
                    op[x, y] = (
                        int(r * t + DARK[0] * (1 - t)),
                        int(g * t + DARK[1] * (1 - t)),
                        int(b * t + DARK[2] * (1 - t)),
                        255,
                    )
    return out


def rounded_legacy(src: Image.Image, px: int) -> Image.Image:
    img = src.resize((px, px), Image.LANCZOS)
    mask = Image.new("L", (px * 4, px * 4), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, px * 4 - 1, px * 4 - 1), radius=int(px * 4 * 0.22), fill=255
    )
    clip = mask.resize((px, px), Image.LANCZOS)
    base = Image.new("RGBA", (px, px), DARK)
    base.paste(img, (0, 0), img)
    base.putalpha(clip)
    return base


def adaptive_layers(src: Image.Image, px: int = 432) -> tuple[Image.Image, Image.Image]:
    bg = Image.new("RGBA", (px, px), DARK)
    fg = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    fit = int(px * 0.72)
    mark = src.resize((fit, fit), Image.LANCZOS)
    fg.paste(mark, ((px - fit) // 2, (px - fit) // 2), mark)
    return bg, fg


def write_mipmaps(src: Image.Image, res: Path) -> None:
    for density, px in LEGACY:
        out = res / f"mipmap-{density}" / "ic_launcher.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        rounded_legacy(src, px).save(out, optimize=True)
        print("wrote", out)


def main() -> None:
    if not SRC.exists():
        raise SystemExit(f"missing source art: {SRC}")
    raw = Image.open(SRC).convert("RGBA")
    icon = crop_squircle(raw)

    BRAND.mkdir(parents=True, exist_ok=True)
    hi = icon.resize((1024, 1024), Image.LANCZOS)
    hi.save(BRAND / "icon.png", optimize=True)

    nodpi = APP_RES / "drawable-nodpi"
    nodpi.mkdir(parents=True, exist_ok=True)
    hi.save(nodpi / "halo_tv_icon.png", optimize=True)
    bg, fg = adaptive_layers(icon, 432)
    fg.save(nodpi / "halo_adaptive_safe.webp", "WEBP", quality=95)
    bg.convert("RGB").save(nodpi / "halo_adaptive_bg.png", optimize=True)

    write_mipmaps(icon, APP_RES)
    write_mipmaps(icon, LITE_RES)

    preview = ROOT / "downloads" / "hitv-icon-preview.png"
    preview.parent.mkdir(parents=True, exist_ok=True)
    hi.resize((512, 512), Image.LANCZOS).save(preview, optimize=True)
    print("wrote", preview)


if __name__ == "__main__":
    main()
