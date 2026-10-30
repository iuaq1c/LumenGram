#!/usr/bin/env python3
"""Recolor the LumenGram web version (a Telegram Web A fork) to Lumen colors.

Applies the same rules as tools/lumen/make_lumen_themes.py does for the
desktop themes:

- light theme: blue tones move to lavender, green "own message" tones move
  to pale lavender, semantic greens (success, online, topic colors) stay;
- dark theme: blue tones move to lavender and dark neutral surfaces get a
  slight violet tint, as in Lumen Dark.

It rewrites src/styles/themes.json and the light base colors in
src/styles/_variables.scss of the given web checkout. Run it on a clean
upstream checkout: the transformation is not idempotent.

Usage:
    python tools/lumen/make_web_theme.py D:/TBuild/lumengram-web
"""

import colorsys
import json
import os
import re
import sys

HUE_FROM, HUE_TO = 175.0, 235.0
HUE_SHIFT = 42.0
SATURATION_SCALE = 0.72

OWN_HUE = 248.0
OWN_SATURATION_SCALE = 0.85
OWN_KEYS = re.compile(r"own|meta-colored|non-contact")

DARK_TINT_HUE = 255.0
DARK_TINT_SATURATION = 0.16
DARK_TINT_MAX_LIGHTNESS = 0.35
NEUTRAL_SATURATION = 0.06

SCSS_KEYS = [
    "color-primary",
    "color-links",
    "color-chat-active",
    "color-selection",
    "color-light-green",
    "color-message-reaction",
    "color-message-reaction-hover",
    "color-message-reaction-own",
    "color-message-reaction-own-hover",
    "color-message-reaction-chosen-hover",
    "color-message-reaction-chosen-hover-own",
    "color-message-non-contact",
]

# Telegram uses its "online" green as the active accent in the light theme
LIGHT_OVERRIDES = {
    "--color-active": "#674DD2",
    "--color-active-darker": "#5a42bb",
}

COLOR_RE = re.compile(r"#([0-9a-fA-F]{6})([0-9a-fA-F]{2})?")


def to_hls(hex6):
    r, g, b = (int(hex6[i:i + 2], 16) / 255.0 for i in (0, 2, 4))
    return colorsys.rgb_to_hls(r, g, b)


def from_hls(h, l, s, upper):
    r, g, b = colorsys.hls_to_rgb(h, l, s)
    result = "".join("%02x" % round(c * 255) for c in (r, g, b))
    return result.upper() if upper else result


def recolor(value, light, own):
    def replace(match):
        hex6, alpha = match.group(1), match.group(2) or ""
        upper = hex6.isupper()
        h, l, s = to_hls(hex6)
        degrees = h * 360.0
        if light and own and s > 0.15 and 70.0 <= degrees <= 170.0:
            h, s = OWN_HUE / 360.0, s * OWN_SATURATION_SCALE
        elif s >= 0.04 and HUE_FROM <= degrees <= HUE_TO:
            h = ((degrees + HUE_SHIFT) % 360.0) / 360.0
            s *= SATURATION_SCALE
        elif (not light
                and s < NEUTRAL_SATURATION
                and l < DARK_TINT_MAX_LIGHTNESS):
            h, s = DARK_TINT_HUE / 360.0, DARK_TINT_SATURATION
        else:
            return match.group(0)
        return "#" + from_hls(h, l, s, upper) + alpha
    return COLOR_RE.sub(replace, value)


def main():
    root = sys.argv[1]
    themes_path = os.path.join(root, "src", "styles", "themes.json")
    with open(themes_path, encoding="utf-8") as f:
        themes = json.load(f)
    for key, (light, dark) in themes.items():
        own = OWN_KEYS.search(key) is not None
        themes[key] = [recolor(light, True, own), recolor(dark, False, own)]
        if key in LIGHT_OVERRIDES:
            themes[key][0] = LIGHT_OVERRIDES[key]
    lines = [
        "  %s: [%s, %s]" % (json.dumps(key), json.dumps(light), json.dumps(dark))
        for key, (light, dark) in themes.items()
    ]
    with open(themes_path, "w", encoding="utf-8", newline="\n") as f:
        f.write("{\n" + ",\n".join(lines) + "\n}\n")
    print("written", themes_path)

    scss_path = os.path.join(root, "src", "styles", "_variables.scss")
    with open(scss_path, encoding="utf-8", newline="") as f:
        scss = f.read()
    for key in SCSS_KEYS:
        own = OWN_KEYS.search(key) is not None or key == "color-light-green"
        scss = re.sub(
            r"(\$" + re.escape(key) + r":\s*)(#[0-9a-fA-F]{6,8})",
            lambda m: m.group(1) + recolor(m.group(2), True, own),
            scss)
    with open(scss_path, "w", encoding="utf-8", newline="") as f:
        f.write(scss)
    print("written", scss_path)


if __name__ == "__main__":
    main()
