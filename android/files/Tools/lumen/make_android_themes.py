#!/usr/bin/env python3
"""Recolor the built-in themes of LumenGram for Android to Lumen colors.

Same rules as the desktop (tools/lumen/make_lumen_themes.py in tdesktop) and
the web version (make_web_theme.py):

- blue hues move to lavender, with a bit less saturation;
- light themes: green "own message" tones move to pale lavender;
- Lumen Dark ("Dark Blue" inside the code): dark neutral surfaces get a slight violet tint;
- Lumen Black ("Night" inside the code): dark surfaces are pulled down to pure black for OLED screens.

Every source file is read from git HEAD, so the script can be run again
after tweaking the constants without recoloring twice.

Usage:
    python tools/lumen/make_android_themes.py
"""

import colorsys
import os
import re
import subprocess

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
MAIN = "TMessagesProj/src/main"

HUE_FROM, HUE_TO = 175.0, 235.0
HUE_SHIFT = 42.0
SATURATION_SCALE = 0.72

OWN_HUE = 248.0
OWN_SATURATION_SCALE = 0.85

DARK_TINT_HUE = 255.0
DARK_TINT_SATURATION = 0.16
DARK_TINT_MAX_LIGHTNESS = 0.35
NEUTRAL_SATURATION = 0.06

# Lumen Black: surfaces at or below the night window background become black.
BLACK_BOTTOM = 0.13
BLACK_RANGE_TOP = 0.30
BLACK_GAMMA = 0.6
BLACK_SATURATION_SCALE = 0.5

# Avatar and attachment colors are a palette of their own, they stay as they are.
IGNORED_RE = re.compile(r"^(avatar_(background|name)|chat_attach|key_avatar_(background|name)|key_chat_attach)")
OWN_RE = re.compile(r"(^|_)(chat_out|key_chat_out)")
# Lumen Black keeps bubbles and pressed states visible on the black background.
KEEP_IN_BLACK_RE = re.compile(r"Bubble|Selected|Selector|selector|Highlight")

ASSETS = {
    "bluebubbles.attheme": "light",
    "arctic.attheme": "light",
    "day.attheme": "light",
    "darkblue.attheme": "dark",
    "night.attheme": "black",
}

# Accent color lists in Theme.java, by the theme name used inside the code.
ACCENT_MODES = {
    "Blue": "light",
    "Dark Blue": "dark",
    "Arctic Blue": "light",
    "Day": "light",
    "Night": "black",
}

THEME_TITLES = {
    "ThemeClassic": "Lumen Light",
    "ThemeDark": "Lumen Dark",
    "ThemeNight": "Lumen Black",
    "ThemeDay": "Lumen Day",
    "ThemeArcticBlue": "Lumen Frost",
}

# Default chat background: Telegram's green gradient becomes lavender.
WALLPAPER_FROM = "0xffdbddbb, 0xff6ba587, 0xffd5d88d, 0xff88b884"
WALLPAPER_TO = "0xffe3dcf8, 0xff9c8ce0, 0xffd9cff6, 0xffb3a4ec"


def git_show(path):
    return subprocess.check_output(["git", "show", "HEAD:" + path], cwd=ROOT).decode("utf-8")


def write(path, text):
    with open(os.path.join(ROOT, path), "w", encoding="utf-8", newline="") as f:
        f.write(text)
    print("written", path)


def recolor_rgb(rgb, mode, own, keep_light=False):
    r, g, b = ((rgb >> 16) & 255) / 255.0, ((rgb >> 8) & 255) / 255.0, (rgb & 255) / 255.0
    h, l, s = colorsys.rgb_to_hls(r, g, b)
    degrees = h * 360.0
    if mode == "light" and own and s > 0.15 and 70.0 <= degrees <= 170.0:
        h, s = OWN_HUE / 360.0, s * OWN_SATURATION_SCALE
    elif s >= 0.04 and HUE_FROM <= degrees <= HUE_TO:
        h, s = ((degrees + HUE_SHIFT) % 360.0) / 360.0, s * SATURATION_SCALE
    elif mode == "dark" and s < NEUTRAL_SATURATION and l < DARK_TINT_MAX_LIGHTNESS:
        h, s = DARK_TINT_HUE / 360.0, DARK_TINT_SATURATION
    if mode == "black" and not keep_light and l < BLACK_RANGE_TOP:
        if l <= BLACK_BOTTOM:
            l = 0.0
        else:
            part = (l - BLACK_BOTTOM) / (BLACK_RANGE_TOP - BLACK_BOTTOM)
            l = BLACK_RANGE_TOP * (part ** BLACK_GAMMA)
            s *= BLACK_SATURATION_SCALE
    r, g, b = colorsys.hls_to_rgb(h, l, s)
    return (round(r * 255) << 16) | (round(g * 255) << 8) | round(b * 255)


def recolor_argb(argb, mode, own, keep_light=False):
    return (argb & 0xFF000000) | recolor_rgb(argb & 0xFFFFFF, mode, own, keep_light)


def convert_attheme(name, mode):
    path = MAIN + "/assets/" + name
    lines = []
    for line in git_show(path).split("\n"):
        key, sep, value = line.partition("=")
        if sep and not IGNORED_RE.match(key) and re.fullmatch(r"-?\d+", value.strip()):
            argb = int(value.strip()) & 0xFFFFFFFF
            argb = recolor_argb(argb, mode, OWN_RE.search(key) is not None, KEEP_IN_BLACK_RE.search(key) is not None)
            if argb >= 0x80000000:
                argb -= 0x100000000
            line = key + "=" + str(argb)
        lines.append(line)
    write(path, "\n".join(lines))


def convert_theme_colors():
    path = MAIN + "/java/org/telegram/ui/ActionBar/ThemeColors.java"

    def replace(match):
        key, literal = match.group(1), match.group(2)
        if IGNORED_RE.match(key):
            return match.group(0)
        argb = recolor_argb(int(literal, 16), "light", OWN_RE.search(key) is not None)
        return match.group(0).replace(literal, "%08x" % argb)

    text = re.sub(r"defaultColors\[(key_\w+)\]\s*=\s*0x([0-9a-fA-F]{8})", replace, git_show(path))
    # Telegram's brand blue is a named constant used by dozens of keys.
    text = re.sub(
        r"(public static final int TELEGRAM_COLOR(?:_TEXT)? = 0x)([0-9a-fA-F]{8})",
        lambda m: m.group(1) + "%08X" % recolor_argb(int(m.group(2), 16), "light", False),
        text)
    write(path, text)


def convert_theme_java():
    path = MAIN + "/java/org/telegram/ui/ActionBar/Theme.java"
    text = git_show(path)
    for name, mode in ACCENT_MODES.items():
        start = text.index('themeInfo.name = "%s";' % name)
        begin = text.index("setAccentColorOptions(", start)
        end = text.index(");", begin)
        block = re.sub(
            r"0x([fF]{2}[0-9a-fA-F]{6})",
            lambda m: "0x%08X" % recolor_argb(int(m.group(1), 16), mode, False),
            text[begin:end])
        text = text[:begin] + block + text[end:]
        # Colors of the small theme preview card.
        preview_end = text.index("setAccentColorOptions(", start)
        preview = re.sub(
            r"(themeInfo\.preview(?:Background|In|Out)Color = 0x)([0-9a-fA-F]{8})",
            lambda m: m.group(1) + "%08x" % recolor_argb(
                int(m.group(2), 16), mode, "previewOut" in m.group(1)),
            text[start:preview_end])
        if mode == "black":
            # The Lumen Black card should look black, its gray is above the darkening range.
            preview = re.sub(r"(previewBackgroundColor = 0x)[0-9a-fA-F]{8}", r"\g<1>ff000000", preview)
            preview = re.sub(r"(previewInColor = 0x)[0-9a-fA-F]{8}", r"\g<1>ff26232e", preview)
        text = text[:start] + preview + text[preview_end:]
    # Hardcoded "home" bubble and wallpaper colors of the dark themes.
    begin = text.index("//override default themes")
    end = text.index("themeAccentsMap.put(themeAccent.id", begin)
    block = re.sub(
        r"0x([fF]{2}[0-9a-fA-F]{6})",
        lambda m: "0x%08X" % recolor_argb(int(m.group(1), 16), "dark", False),
        text[begin:end])
    text = text[:begin] + block + text[end:]
    assert text.count(WALLPAPER_FROM) == 1
    text = text.replace(WALLPAPER_FROM, WALLPAPER_TO)
    # Names shown to the user; the names inside the code stay, other code depends on them.
    for string, title in THEME_TITLES.items():
        old = "return getString(R.string.%s);" % string
        assert text.count(old) == 1, old
        text = text.replace(old, 'return "%s";' % title)
    write(path, text)


def main():
    for name, mode in ASSETS.items():
        convert_attheme(name, mode)
    convert_theme_colors()
    convert_theme_java()


if __name__ == "__main__":
    main()
