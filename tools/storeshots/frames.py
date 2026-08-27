"""Draws the phone mockup: rounded body, bezel, side buttons, screen inset.

The frame is generated rather than composited from a PNG asset so it adapts to
whatever aspect ratio the raw screenshot happens to have (1080x2340, 1440x3120,
foldables). Everything is drawn at 4x and downsampled, which is what keeps the
corner radii and the 3px bezel highlight from looking jagged.
"""
from PIL import Image, ImageDraw, ImageFilter

SS = 4  # supersample factor


def _rr(draw, box, radius, **kw):
    draw.rounded_rectangle(box, radius=radius, **kw)


def render_phone(screenshot, target_h, body_color=(28, 28, 30), rim=(198, 168, 108)):
    """Return an RGBA phone mockup image of height ~target_h containing `screenshot`.

    body_color  outer chassis; rim is the thin metallic edge highlight seen on the
                S25-Ultra-style frames in the existing store shots.
    """
    sw, sh = screenshot.size
    ratio = sw / sh

    # Screen height drives everything; bezel is a fixed fraction of screen width.
    scr_h = target_h * SS
    scr_w = int(scr_h * ratio)
    bez = max(6 * SS, int(scr_w * 0.018))
    body_w, body_h = scr_w + bez * 2, scr_h + bez * 2

    pad = 8 * SS  # room for the button nubs sticking out
    canvas = Image.new("RGBA", (body_w + pad * 2, body_h + pad * 2), (0, 0, 0, 0))
    d = ImageDraw.Draw(canvas)

    br = int(body_w * 0.085)   # body corner radius
    sr = int(scr_w * 0.075)    # screen corner radius
    x0, y0 = pad, pad
    x1, y1 = pad + body_w, pad + body_h

    # Side buttons first so the body overlaps their inner edge.
    btn_w = int(bez * 0.55)
    for top, height in ((0.22, 0.055), (0.30, 0.10)):
        by = y0 + int(body_h * top)
        _rr(d, (x1 - btn_w, by, x1 + btn_w * 2, by + int(body_h * height)),
            radius=btn_w, fill=(80, 78, 82, 255))

    # Chassis, then the metallic rim as a 1px-ish inset outline.
    _rr(d, (x0, y0, x1, y1), radius=br, fill=body_color + (255,))
    _rr(d, (x0, y0, x1, y1), radius=br, outline=rim + (255,), width=max(2, SS))

    # Screen well: punch the screenshot in with rounded corners.
    sx0, sy0 = x0 + bez, y0 + bez
    shot = screenshot.convert("RGB").resize((scr_w, scr_h), Image.LANCZOS)
    mask = Image.new("L", (scr_w, scr_h), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, scr_w - 1, scr_h - 1), radius=sr, fill=255)
    canvas.paste(shot, (sx0, sy0), mask)

    out_w = canvas.width // SS
    out_h = canvas.height // SS
    return canvas.resize((out_w, out_h), Image.LANCZOS)


def drop_shadow(img, blur=26, offset=(0, 14), opacity=110):
    """Soft shadow behind the phone so it lifts off the gradient."""
    a = img.split()[-1]
    sh = Image.new("RGBA", img.size, (0, 0, 0, 0))
    sh.putalpha(a.point(lambda v: int(v * opacity / 255)))
    pad = blur * 3
    big = Image.new("RGBA", (img.width + pad * 2, img.height + pad * 2), (0, 0, 0, 0))
    big.paste(sh, (pad + offset[0], pad + offset[1]))
    return big.filter(ImageFilter.GaussianBlur(blur)), pad
