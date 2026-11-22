#!/usr/bin/env python3
"""Generate the LumenGram chat wallpaper pattern for the web version.

The web client (a Telegram Web A fork) uses the pattern as a CSS mask over
the animated wallpaper gradient, so only the shape alpha matters. The tile
is seamless: shapes near an edge are repeated on the opposite side.

Usage:
    python tools/lumen/make_web_pattern.py D:/TBuild/lumengram-web/src/assets/lumen-pattern.svg
"""

import math
import random
import sys

SIZE = 1440
SEED = 20261021
DOTS = 260
SPARKLES = 34
MOONS = 9
MARGIN = 60


def sparkle_path(cx, cy, radius):
    inner = radius * 0.24
    points = []
    for i in range(8):
        angle = math.pi / 4 * i - math.pi / 2
        r = inner if i % 2 else radius
        points.append("%.1f %.1f" % (cx + r * math.cos(angle), cy + r * math.sin(angle)))
    return '<path d="M%sZ"/>' % "L".join(points)


def moon_path(cx, cy, radius, rotation):
    # Crescent: outer arc of the full circle, inner arc of a shifted circle.
    cut = radius * 0.82
    shift = radius * 0.45
    a = math.radians(rotation)
    ox, oy = cx + shift * math.cos(a), cy + shift * math.sin(a)
    # Intersection points of the two circles.
    d = shift
    x = (d * d + radius * radius - cut * cut) / (2 * d)
    h = math.sqrt(max(radius * radius - x * x, 0))
    ux, uy = math.cos(a), math.sin(a)
    px, py = cx + ux * x, cy + uy * x
    p1 = (px - uy * h, py + ux * h)
    p2 = (px + uy * h, py - ux * h)
    return ('<path d="M%.1f %.1fA%.1f %.1f 0 1 1 %.1f %.1fA%.1f %.1f 0 0 0 %.1f %.1fZ"/>'
            % (p1[0], p1[1], radius, radius, p2[0], p2[1], cut, cut, p1[0], p1[1]))


def wrapped(x, y, reach):
    """Positions of a shape and its copies across the tile edges."""
    xs = [x] + ([x + SIZE] if x < reach else []) + ([x - SIZE] if x > SIZE - reach else [])
    ys = [y] + ([y + SIZE] if y < reach else []) + ([y - SIZE] if y > SIZE - reach else [])
    return [(a, b) for a in xs for b in ys]


def main():
    rng = random.Random(SEED)
    shapes = []
    for _ in range(DOTS):
        x, y = rng.uniform(0, SIZE), rng.uniform(0, SIZE)
        r = rng.choice([2.2, 2.8, 3.4, 4.2, 5.2])
        for a, b in wrapped(x, y, r):
            shapes.append('<circle cx="%.1f" cy="%.1f" r="%.1f"/>' % (a, b, r))
    for _ in range(SPARKLES):
        x, y = rng.uniform(0, SIZE), rng.uniform(0, SIZE)
        r = rng.uniform(12, 30)
        for a, b in wrapped(x, y, r):
            shapes.append(sparkle_path(a, b, r))
    for _ in range(MOONS):
        x, y = rng.uniform(MARGIN, SIZE - MARGIN), rng.uniform(MARGIN, SIZE - MARGIN)
        shapes.append(moon_path(x, y, rng.uniform(22, 34), rng.uniform(-60, 0)))
    svg = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d">\n%s\n</svg>\n'
           % (SIZE, SIZE, "\n".join(shapes)))
    with open(sys.argv[1], "w", encoding="utf-8", newline="\n") as f:
        f.write(svg)
    print("written", sys.argv[1], len(shapes), "shapes")


if __name__ == "__main__":
    main()
