"""Backgrounds, headline text and emoji badges."""
import math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

EMOJI_FONT = "/System/Library/Fonts/Apple Color Emoji.ttc"
EMOJI_SIZES = (160,)  # the .ttc only carries one strike; scale after rendering


def mesh_gradient(size, stops, seed_shift=0.0):
    """Soft multi-point 'mesh' gradient like the pink/violet/blue store shots.

    `stops` is a list of ((x, y), (r, g, b)) with x/y in 0..1. Rendered small and
    upscaled — blurring a 64px canvas is what produces the smooth blend without a
    per-pixel loop over 2M pixels.
    """
    w, h = size
    small = Image.new("RGB", (64, 64))
    px = small.load()
    pts = [((x + seed_shift) % 1.0, y, c) for (x, y), c in stops]
    for j in range(64):
        for i in range(64):
            u, v = i / 63, j / 63
            tw = 0.0
            acc = [0.0, 0.0, 0.0]
            for cx, cy, col in pts:
                d = max((u - cx) ** 2 + (v - cy) ** 2, 1e-4)
                wgt = 1.0 / (d ** 1.35)
                tw += wgt
                for k in range(3):
                    acc[k] += col[k] * wgt
            px[i, j] = tuple(int(min(255, a / tw)) for a in acc)
    return small.resize((w, h), Image.LANCZOS).filter(ImageFilter.GaussianBlur(w * 0.02))


# Palettes sampled from the existing listing images.
PALETTES = {
    "candy":  [((0.05, 0.05), (196, 168, 246)), ((0.95, 0.10), (150, 190, 250)),
               ((0.10, 0.95), (120, 205, 235)), ((0.90, 0.92), (250, 170, 205)),
               ((0.50, 0.50), (243, 186, 226))],
    "blush":  [((0.08, 0.10), (252, 214, 232)), ((0.92, 0.20), (247, 160, 205)),
               ((0.15, 0.90), (176, 214, 250)), ((0.88, 0.95), (233, 176, 246)),
               ((0.50, 0.55), (250, 190, 220))],
    "violet": [((0.10, 0.08), (170, 158, 248)), ((0.90, 0.12), (233, 168, 240)),
               ((0.12, 0.92), (150, 200, 248)), ((0.90, 0.90), (204, 170, 250)),
               ((0.50, 0.50), (196, 178, 250))],
    "sunset": [((0.06, 0.08), (255, 200, 180)), ((0.94, 0.14), (250, 160, 190)),
               ((0.10, 0.94), (215, 175, 250)), ((0.92, 0.88), (255, 190, 165)),
               ((0.50, 0.50), (252, 186, 196))],
    "mint":   [((0.08, 0.06), (176, 236, 220)), ((0.92, 0.16), (160, 205, 250)),
               ((0.12, 0.92), (214, 240, 200)), ((0.88, 0.94), (168, 224, 244)),
               ((0.50, 0.50), (186, 232, 230))],
}
PALETTE_CYCLE = ["candy", "blush", "violet", "sunset", "mint"]


def wrap(draw, text, font, max_w):
    words, lines, cur = text.split(), [], ""
    for word in words:
        trial = f"{cur} {word}".strip()
        if draw.textlength(trial, font=font) <= max_w or not cur:
            cur = trial
        else:
            lines.append(cur)
            cur = word
    if cur:
        lines.append(cur)
    return lines


def fit_headline(draw, text, font_path, max_w, max_size, min_size=34, max_lines=3):
    """Shrink until the headline fits `max_lines` — long app names wrap to 3."""
    size = max_size
    while size > min_size:
        font = ImageFont.truetype(font_path, size)
        lines = wrap(draw, text, font, max_w)
        if len(lines) <= max_lines:
            return font, lines
        size -= 3
    font = ImageFont.truetype(font_path, min_size)
    return font, wrap(draw, text, font, max_w)


def draw_headline(canvas, text, font_path, color, top, max_w, max_size, line_gap=1.16):
    d = ImageDraw.Draw(canvas)
    font, lines = fit_headline(d, text, font_path, max_w, max_size)
    lh = int(font.size * line_gap)
    y = top
    for line in lines:
        w = d.textlength(line, font=font)
        d.text(((canvas.width - w) / 2, y), line, font=font, fill=color)
        y += lh
    return y  # bottom of the text block


def emoji_badge(emoji, diameter, bg=(255, 255, 255)):
    """White circular chip with a colour emoji, like the ⭐/🤩 badges."""
    SS = 3
    d_px = diameter * SS
    chip = Image.new("RGBA", (d_px, d_px), (0, 0, 0, 0))
    ImageDraw.Draw(chip).ellipse((0, 0, d_px - 1, d_px - 1), fill=bg + (255,))
    try:
        font = ImageFont.truetype(EMOJI_FONT, EMOJI_SIZES[0])
        layer = Image.new("RGBA", (200, 200), (0, 0, 0, 0))
        ImageDraw.Draw(layer).text((10, 10), emoji, font=font, embedded_color=True)
        bbox = layer.getbbox()
        if bbox:
            glyph = layer.crop(bbox)
            target = int(d_px * 0.62)
            scale = target / max(glyph.size)
            glyph = glyph.resize((max(1, int(glyph.width * scale)),
                                  max(1, int(glyph.height * scale))), Image.LANCZOS)
            chip.paste(glyph, ((d_px - glyph.width) // 2, (d_px - glyph.height) // 2), glyph)
    except Exception:
        pass  # no emoji font -> plain white chip, still looks intentional
    return chip.resize((diameter, diameter), Image.LANCZOS)
