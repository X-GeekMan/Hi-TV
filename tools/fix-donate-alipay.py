#!/usr/bin/env python3
"""Recrop Alipay donate QR with a quiet zone so rounded UI clips don't eat finders."""
from __future__ import annotations

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SRC = Path(
    r"C:\Users\Administrator\.cursor\projects\c-Users-Administrator-Desktop-HiTV"
    r"\assets\ALQRCode-50138c8e-00cb-4bca-9421-4c88c2f4906c.jpg"
)


def main() -> None:
    im = Image.open(SRC).convert("RGB")
    w, h = im.size
    px = im.load()

    def lum(x: int, y: int) -> float:
        r, g, b = px[x, y]
        return (r + g + b) / 3.0

    def is_blue(x: int, y: int) -> bool:
        r, g, b = px[x, y]
        return b > 160 and b > r + 30 and b > g + 15

    # First solid Alipay-blue band (the headline strip), not the footer blue.
    blue_rows = []
    for y in range(h):
        blue = sum(1 for x in range(w // 5, 4 * w // 5, 2) if is_blue(x, y))
        if blue > 80:
            blue_rows.append(y)
    if not blue_rows:
        raise SystemExit("no blue band")
    # First contiguous blue run = header strip above the white card.
    start = prev = blue_rows[0]
    for y in blue_rows[1:]:
        if y == prev + 1:
            prev = y
        else:
            break
    banner = prev
    print("header blue", start, banner)

    # QR rows: strong black+white checker after the header.
    qr_rows = []
    for y in range(banner + 5, h):
        dark = sum(1 for x in range(w // 5, 4 * w // 5) if lum(x, y) < 90)
        light = sum(1 for x in range(w // 5, 4 * w // 5) if lum(x, y) > 220)
        if dark >= 50 and light >= 50:
            qr_rows.append(y)
    if not qr_rows:
        raise SystemExit("no qr rows")
    qs = qr_rows[0]
    qe = qs
    for y in qr_rows:
        if y <= qe + 3:
            qe = y
        else:
            break
    print("qr rows", qs, qe)

    left, right = w, 0
    for y in range(qs, qe + 1):
        for x in range(w):
            if lum(x, y) < 90:
                left = min(left, x)
                right = max(right, x)
    print("qr cols", left, right, "size", right - left + 1, qe - qs + 1)

    pad = 6
    L, T, R, B = left - pad, qs - pad, right + pad, qe + pad
    side = max(R - L + 1, B - T + 1)
    cx = (L + R) / 2
    cy = (T + B) / 2
    half = side / 2
    L2, T2 = int(cx - half), int(cy - half)
    crop = im.crop((max(0, L2), max(0, T2), min(w, L2 + side), min(h, T2 + side)))
    if crop.size[0] != side or crop.size[1] != side:
        square = Image.new("RGB", (side, side), (255, 255, 255))
        square.paste(crop, ((side - crop.size[0]) // 2, (side - crop.size[1]) // 2))
        crop = square

    qz = max(28, side // 8)
    out = Image.new("RGB", (side + 2 * qz, side + 2 * qz), (255, 255, 255))
    out.paste(crop, (qz, qz))
    out = out.resize((512, 512), Image.LANCZOS)

    targets = [
        ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "donate_alipay.png",
        ROOT / "lite" / "src" / "main" / "res" / "drawable-nodpi" / "donate_alipay.png",
        ROOT / "downloads" / "donate_alipay_preview.png",
    ]
    for path in targets:
        path.parent.mkdir(parents=True, exist_ok=True)
        out.save(path, optimize=True)
        print("wrote", path)


if __name__ == "__main__":
    main()
