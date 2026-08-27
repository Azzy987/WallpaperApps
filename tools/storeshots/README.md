# Play Store screenshot generator

Turns raw phone captures into finished listing images — mockup frame, gradient
background, headline and emoji badge — so a new app's screenshots no longer go
through a website by hand.

## Use

```
python3 tools/storeshots/make_screenshots.py <module> --capture
```

`--capture` pulls the shots off your phone over adb: it prompts before each one,
you navigate to the screen, press Enter. Drop the flag if you already have PNGs
in `storeshots/<module>/raw/`.

Output lands in `storeshots/<module>/out/` at 1080x1920 — Play's recommended
phone screenshot size — numbered in upload order.

## Captions

First run writes `storeshots/<module>/captions.txt` with a starter set. One
headline per screenshot, matched to `raw/` in sorted order:

```
Welcome to {app} – Redefine Your Screen | 🤩
Discover Wallpapers That Define Your Style
```

`{app}` expands to `app_name` from the module's `strings.xml`, so copying one
app's `captions.txt` to another needs no editing. A trailing `| <emoji>` adds a
badge chip.

## Look

Backgrounds cycle through five palettes sampled from the existing listings
(`--palette blush` pins one instead). Layouts alternate headline-above,
headline-below and a bleed variant so the eight images don't look identical.
Font is Nunito Black, which ships with macOS.
