#!/usr/bin/env python3
"""Generate the Lumengram application icon files in Telegram/Resources/art.

The icon is a lavender-to-indigo sphere with a white crescent, a sparkle
star and a few small stars. Everything is drawn supersampled with Pillow,
so the icon can be regenerated after tweaking the constants below.

Usage:
    python tools/lumen/make_icon.py                  # write all icon files
    python tools/lumen/make_icon.py --preview OUT.png  # only a preview sheet
    python tools/lumen/make_icon.py --web PUBLIC_DIR   # web version icons
"""

import math
import os
import random
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ART = os.path.join(ROOT, "Telegram", "Resources", "art")

SUPERSAMPLE = 4
TOP_COLOR = (150, 132, 245)
BOTTOM_COLOR = (38, 26, 96)
GLOW_COLOR = (200, 190, 255)
STAR_SEED = 20261020


def gradient(size):
    """Diagonal gradient from TOP_COLOR (top-left) to BOTTOM_COLOR."""
    ramp = Image.linear_gradient("L").rotate(45, expand=True)
    ramp = ramp.resize((size * 2, size * 2)).crop(
        (size // 2, size // 2, size // 2 + size, size // 2 + size))
    top = Image.new("RGB", (size, size), TOP_COLOR)
    bottom = Image.new("RGB", (size, size), BOTTOM_COLOR)
    return Image.composite(bottom, top, ramp)


def circle_mask(size, box):
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse(box, fill=255)
    return mask


def sparkle(draw, cx, cy, radius, fill):
    inner = radius * 0.2
    points = []
    for i in range(8):
        angle = math.pi / 4 * i - math.pi / 2
        r = radius if i % 2 == 0 else inner
        points.append((cx + r * math.cos(angle), cy + r * math.sin(angle)))
    draw.polygon(points, fill=fill)


def render(size, margin, details=True, square=False):
    big = size * SUPERSAMPLE
    pad = big * margin
    box = (pad, pad, big - pad, big - pad)
    radius = (big - 2 * pad) / 2
    cx = cy = big / 2

    image = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    sphere = gradient(big).convert("RGBA")

    light = Image.new("L", (big, big), 0)
    ImageDraw.Draw(light).ellipse(
        (cx - radius * 1.1, cy - radius * 1.25,
         cx + radius * 0.3, cy + radius * 0.15),
        fill=70)
    light = light.filter(ImageFilter.GaussianBlur(radius * 0.35))
    sphere = Image.composite(
        Image.new("RGBA", (big, big), (255, 255, 255, 255)),
        sphere,
        light)

    overlay = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)
    if details:
        rng = random.Random(STAR_SEED)
        for _ in range(26):
            angle = rng.uniform(0, 2 * math.pi)
            distance = radius * math.sqrt(rng.uniform(0.05, 0.85))
            x = cx + distance * math.cos(angle)
            y = cy + distance * math.sin(angle)
            r = radius * rng.uniform(0.008, 0.02)
            alpha = rng.randint(90, 210)
            draw.ellipse((x - r, y - r, x + r, y + r),
                         fill=(255, 255, 255, alpha))

    moon_r = radius * 0.46
    moon_cx = cx - radius * 0.08
    moon_cy = cy + radius * 0.04
    cut_r = moon_r * 0.86
    cut_cx = moon_cx + moon_r * 0.42
    cut_cy = moon_cy - moon_r * 0.30
    moon = Image.new("L", (big, big), 0)
    ImageDraw.Draw(moon).ellipse(
        (moon_cx - moon_r, moon_cy - moon_r,
         moon_cx + moon_r, moon_cy + moon_r), fill=255)
    cut = Image.new("L", (big, big), 0)
    ImageDraw.Draw(cut).ellipse(
        (cut_cx - cut_r, cut_cy - cut_r,
         cut_cx + cut_r, cut_cy + cut_r), fill=255)
    moon = ImageChops.subtract(moon, cut)

    star = Image.new("L", (big, big), 0)
    star_r = radius * (0.24 if details else 0.30)
    sparkle(ImageDraw.Draw(star),
            cx + radius * 0.30, cy - radius * 0.26, star_r, 255)
    if details:
        sparkle(ImageDraw.Draw(star),
                cx + radius * 0.46, cy + radius * 0.12, star_r * 0.38, 200)

    shapes = ImageChops.lighter(moon, star)
    glow = shapes.filter(ImageFilter.GaussianBlur(radius * 0.09))
    glow = glow.point(lambda v: int(v * 0.75))
    overlay = Image.alpha_composite(
        overlay,
        Image.composite(
            Image.new("RGBA", (big, big), GLOW_COLOR + (255,)),
            Image.new("RGBA", (big, big), GLOW_COLOR + (0,)),
            glow))
    overlay = Image.alpha_composite(
        overlay,
        Image.composite(
            Image.new("RGBA", (big, big), (255, 255, 255, 255)),
            Image.new("RGBA", (big, big), (255, 255, 255, 0)),
            shapes))

    content = Image.alpha_composite(sphere, overlay)
    if square:
        image = content
    else:
        image.paste(content, (0, 0), circle_mask(big, box))
    return image.resize((size, size), Image.LANCZOS)


def details_for(size):
    return size >= 48


def write_png(name, size, margin):
    path = os.path.join(ART, name)
    render(size, margin, details_for(size)).save(path)
    print("written", os.path.relpath(path, ROOT))


def write_ico(name, margin):
    path = os.path.join(ART, name)
    sizes = [16, 24, 32, 48, 256]
    images = [render(s, margin if s >= 48 else 0.0, details_for(s))
              for s in sizes]
    images[-1].save(path, sizes=[(s, s) for s in sizes],
                    append_images=images[:-1])
    print("written", os.path.relpath(path, ROOT))


def preview(path):
    sizes = [256, 128, 64, 48, 32, 24, 16]
    sheet = Image.new("RGBA", (sum(sizes) + 20 * len(sizes) + 20, 300),
                      (30, 28, 40, 255))
    x = 20
    for s in sizes:
        icon = render(s, 0.04, details_for(s))
        sheet.alpha_composite(icon, (x, 20 + (256 - s) // 2))
        x += s + 20
    sheet.save(path)
    print("preview", path)


def write_web(public):
    """Icons of the LumenGram web version (a Telegram Web A fork)."""
    def save(name, image):
        image.save(os.path.join(public, name))
        print("written", name)

    margin = 0.04
    save("favicon-16x16.png", render(16, 0.0, False))
    save("favicon-32x32.png", render(32, 0.0, False))
    for s in [192, 384, 512]:
        for suffix in ["", "-dev"]:
            save("icon%s-%dx%d.png" % (suffix, s, s), render(s, margin))
            save("icon-square%s-%dx%d.png" % (suffix, s, s),
                 render(s, 0.0, True, square=True))
    for name in ["apple-touch-icon.png", "apple-touch-icon-dev.png"]:
        save(name, render(180, 0.0, True, square=True))
    save("mstile-150x150.png", render(270, margin))
    save("icon-electron-macos.png", render(512, margin))
    ico = [render(s, 0.0, details_for(s)) for s in [16, 32, 48]]
    ico[-1].save(os.path.join(public, "favicon.ico"),
                 sizes=[(16, 16), (32, 32), (48, 48)],
                 append_images=ico[:-1])
    print("written favicon.ico")


def main():
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])
        return
    if "--web" in sys.argv:
        write_web(sys.argv[sys.argv.index("--web") + 1])
        return
    margin = 0.04
    for s in [16, 32, 48, 64, 128, 256, 512]:
        write_png("icon%d.png" % s, s, margin if s >= 48 else 0.0)
        write_png("icon%d@2x.png" % s, s * 2, margin)
    write_png("logo_256.png", 256, margin)
    write_png("logo_256_no_margin.png", 256, 0.0)
    write_png("icon_round512@2x.png", 1024, 0.05)
    write_ico("icon256.ico", margin)


if __name__ == "__main__":
    main()
