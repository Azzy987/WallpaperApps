#!/usr/bin/env python3
"""Generate Play Store listing screenshots from raw device captures.

    python3 tools/storeshots/make_screenshots.py iphone17
    python3 tools/storeshots/make_screenshots.py iphone17 --capture

Reads raw PNGs from  storeshots/<module>/raw/  (numbered in display order) and
writes finished 1080x1920 listing images to  storeshots/<module>/out/ .

Headlines come from storeshots/<module>/captions.txt, one per line, matched to
the raw files in sorted order. "{app}" in a caption expands to the app name read
from <module>/src/main/res/values/strings.xml, so the same captions.txt can be
copied to a new app and needs no editing.
"""
import argparse
import os
import re
import subprocess
import sys
import time

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import frames
import paint

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
FONT = "/System/Library/Fonts/Supplemental/Nunito-Black.ttf"
INK = (26, 46, 34)          # the near-black green in the existing shots
CANVAS = (1080, 1920)       # Play's recommended phone screenshot size

# Caption set used when an app has no captions.txt yet. Deliberately generic so a
# brand-new app produces a usable listing before anything is hand-written.
DEFAULT_CAPTIONS = [
    "Welcome to {app} – Redefine Your Screen",
    "Discover Wallpapers That Define Your Style",
    "Organized Collections for Quick Discovery",
    "Stay Ahead with Trending Wallpapers",
    "Save Your Favorite Wallpapers Instantly",
    "Thousands of High-Quality Designs",
    "Experience Wallpapers in Full Glory",
    "Edit, customize, and make wallpapers truly yours.",
]
DEFAULT_EMOJI = ["🤩", "", "", "⭐", "💜", "", "", ""]


def app_name(module):
    path = os.path.join(ROOT, module, "src", "main", "res", "values", "strings.xml")
    with open(path, encoding="utf-8") as f:
        m = re.search(r'<string name="app_name">([^<]*)</string>', f.read())
    if not m:
        sys.exit(f"no app_name in {path}")
    return m.group(1).strip()


def capture(dest, count, delay):
    """Pull `count` screenshots off the attached device, pausing between each."""
    devices = subprocess.run(["adb", "devices"], capture_output=True, text=True).stdout
    if not [l for l in devices.splitlines()[1:] if "\tdevice" in l]:
        sys.exit("no adb device attached — connect your phone with USB debugging on")
    os.makedirs(dest, exist_ok=True)
    for i in range(1, count + 1):
        input(f"  navigate to screen {i}/{count}, then press Enter to capture… ")
        out = os.path.join(dest, f"{i:02d}.png")
        raw = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout
        with open(out, "wb") as f:
            f.write(raw)
        print(f"  saved {out}")
        time.sleep(delay)


def compose(shot, caption, emoji, palette, layout, tilt):
    """Build one 1080x1920 listing image."""
    W, H = CANVAS
    bg = paint.mesh_gradient((W, H), paint.PALETTES[palette]).convert("RGBA")

    margin = 70
    text_size = 74
    gap = 56

    # Reserve the text band first, then size the phone to whatever height is left,
    # so a 3-line headline shrinks the phone instead of pushing it off the canvas.
    _d = ImageDraw.Draw(bg)
    font, lines = paint.fit_headline(_d, caption, FONT, W - margin * 2, text_size)
    text_h = int(font.size * 1.16) * len(lines)

    if layout == "below":
        text_top = H - margin - text_h
        avail = text_top - gap - margin
    elif layout == "bleed":
        text_top = 110
        avail = int((H - (text_top + text_h + gap)) * 1.14)  # runs past the edge
    else:  # "above"
        text_top = 110
        avail = H - (text_top + text_h + gap) - margin

    phone = frames.render_phone(shot, avail)
    if tilt:
        phone = phone.rotate(tilt, resample=Image.BICUBIC, expand=True)

    # A tilt makes the frame taller/wider; scale back down so it still fits.
    if phone.height > avail:
        k = avail / phone.height
        phone = phone.resize((int(phone.width * k), int(phone.height * k)), Image.LANCZOS)
    max_w = W - margin
    if phone.width > max_w:
        k = max_w / phone.width
        phone = phone.resize((int(phone.width * k), int(phone.height * k)), Image.LANCZOS)

    px = (W - phone.width) // 2
    py = margin if layout == "below" else text_top + text_h + gap

    shadow, spad = frames.drop_shadow(phone)
    bg.alpha_composite(shadow, (px - spad, py - spad))
    bg.alpha_composite(phone, (px, py))

    paint.draw_headline(bg, caption, FONT, INK, text_top, W - margin * 2, text_size)

    if emoji:
        size = 150
        badge = paint.emoji_badge(emoji, size)
        # Hang the chip over the phone's left or right edge, alternating by parity
        # of the caption length so consecutive shots do not all badge the same side.
        right = len(caption) % 2 == 0
        bx = px + phone.width - size // 2 if right else px - size // 2
        bx = max(24, min(bx, W - size - 24))
        by = max(24, min(py + int(phone.height * 0.16), H - size - 24))
        bg.alpha_composite(badge, (int(bx), int(by)))

    return bg.convert("RGB")


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("module", help="gradle module, e.g. iphone17")
    ap.add_argument("--capture", action="store_true", help="pull raw shots off adb first")
    ap.add_argument("--count", type=int, default=8, help="shots to capture (default 8)")
    ap.add_argument("--delay", type=float, default=0.5)
    ap.add_argument("--palette", default="cycle",
                    help="one of %s, or 'cycle'" % ", ".join(paint.PALETTE_CYCLE))
    args = ap.parse_args()

    name = app_name(args.module)
    base = os.path.join(ROOT, "storeshots", args.module)
    raw_dir, out_dir = os.path.join(base, "raw"), os.path.join(base, "out")

    if args.capture:
        capture(raw_dir, args.count, args.delay)

    if not os.path.isdir(raw_dir):
        sys.exit(f"no raw screenshots — put PNGs in {raw_dir} or rerun with --capture")
    raws = sorted(f for f in os.listdir(raw_dir) if f.lower().endswith((".png", ".jpg", ".jpeg")))
    if not raws:
        sys.exit(f"{raw_dir} is empty")

    cap_file = os.path.join(base, "captions.txt")
    if os.path.exists(cap_file):
        with open(cap_file, encoding="utf-8") as f:
            entries = [l.strip() for l in f if l.strip() and not l.startswith("#")]
    else:
        entries = list(DEFAULT_CAPTIONS)
        os.makedirs(base, exist_ok=True)
        with open(cap_file, "w", encoding="utf-8") as f:
            f.write("# One headline per screenshot, matched to raw/ in sorted order.\n"
                    "# {app} expands to the app_name from strings.xml.\n"
                    "# Optional trailing '| emoji' adds a badge, e.g.  Save Favorites | 💜\n")
            for c, e in zip(entries, DEFAULT_EMOJI):
                f.write(f"{c}{(' | ' + e) if e else ''}\n")
        print(f"wrote starter captions to {cap_file} — edit and rerun to tweak wording")

    os.makedirs(out_dir, exist_ok=True)
    layouts = ["above", "above", "below", "above", "above", "above", "bleed", "below"]
    tilts = [-4, 0, 0, 0, 3, 0, 0, 0]

    for i, fn in enumerate(raws):
        entry = entries[i] if i < len(entries) else DEFAULT_CAPTIONS[i % len(DEFAULT_CAPTIONS)]
        emoji = ""
        if "|" in entry:
            entry, emoji = (p.strip() for p in entry.rsplit("|", 1))
        caption = entry.replace("{app}", name)
        if i >= len(entries):
            emoji = DEFAULT_EMOJI[i % len(DEFAULT_EMOJI)]

        palette = (paint.PALETTE_CYCLE[i % len(paint.PALETTE_CYCLE)]
                   if args.palette == "cycle" else args.palette)
        shot = Image.open(os.path.join(raw_dir, fn))
        img = compose(shot, caption, emoji, palette,
                      layouts[i % len(layouts)], tilts[i % len(tilts)])
        out = os.path.join(out_dir, f"{i + 1:02d}.png")
        img.save(out, optimize=True)
        print(f"  {fn} -> {os.path.relpath(out, ROOT)}  [{caption}]")

    print(f"\n{len(raws)} screenshots for {name} in {os.path.relpath(out_dir, ROOT)}")


if __name__ == "__main__":
    main()
