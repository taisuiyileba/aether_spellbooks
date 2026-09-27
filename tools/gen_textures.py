"""
Generates the textures for Aether Spellbooks.

All art here is original and procedurally drawn by this script. The Aether's and Iron's Spells'
assets are All Rights Reserved and must not be copied, traced or recoloured; only the UV *layout*
of Iron's Spells' 3D spellbook template is followed so books render with its model.

Techniques:
  * items      - hand-authored pixel maps / procedural pixel art quantised to 3-5 shade ramps,
                 selective (hue-tinted) outlines, light from the top-left.
  * spell icons - painterly: composed at 128x128 with gradients, soft shapes, blur glows and
                 noise, then downsampled to 16x16 and contrast/saturation boosted.
  * particles / entity sprites - soft sprites with premultiplied alpha falloff.

Everything is deterministic (all randomness is seeded).
Replace any PNG with hand-drawn art later; re-running this script overwrites them.

Requires: Pillow, numpy
Usage:  python tools/gen_textures.py            (also writes build/texture_preview.png)
"""
import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.normpath(os.path.join(HERE, ".."))
ROOT = os.path.join(PROJECT, "src", "main", "resources", "assets", "aether_spellbooks", "textures")
PREVIEW = os.path.join(PROJECT, "build", "texture_preview.png")

WRITTEN = []  # (relative path, image) - used for the preview sheet


def save(img, *path):
    p = os.path.join(ROOT, *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)
    WRITTEN.append(("/".join(path), img))


# ============================================================================ colour helpers
def hexa(h, a=255):
    """'#rrggbb' -> (r, g, b, a) ints"""
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def col(c):
    """'#rrggbb' -> float32 rgb array (0..1); arrays pass through."""
    if isinstance(c, str):
        return np.array(hexa(c)[:3], np.float32) / 255.0
    return np.asarray(c, np.float32)


def lerp(a, b, t):
    t = np.asarray(t, np.float32)
    if t.ndim:
        t = t[..., None]
    return a * (1 - t) + b * t


def ramp_map(t, stops):
    """Piecewise-linear colour map. stops = [(pos, '#hex'), ...] sorted by pos."""
    t = np.clip(np.asarray(t, np.float32), 0, 1)
    ps = [p for p, _ in stops]
    cs = [col(c) for _, c in stops]
    out = np.empty(t.shape + (3,), np.float32)
    out[:] = cs[0]
    for i in range(len(stops) - 1):
        p0, p1 = ps[i], ps[i + 1]
        m = (t >= p0) & (t <= p1)
        u = ((t[m] - p0) / max(p1 - p0, 1e-6))[:, None]
        out[m] = cs[i] * (1 - u) + cs[i + 1] * u
    out[t > ps[-1]] = cs[-1]
    return out


# ============================================================================ float painting kit
N = 128  # composition size for painterly icons


def grid(n):
    ys, xs = np.mgrid[0:n, 0:n].astype(np.float32)
    return xs + 0.5, ys + 0.5


def dist(n, cx, cy):
    xs, ys = grid(n)
    return np.hypot(xs - cx, ys - cy)


def blur(a, sigma):
    """Separable gaussian blur of a 2D (or HxWxC) float array, zero padded."""
    if sigma <= 0:
        return a.copy()
    r = max(1, int(sigma * 3))
    k = np.exp(-0.5 * (np.arange(-r, r + 1) / sigma) ** 2).astype(np.float32)
    k /= k.sum()

    def conv(x, axis):
        pad = [(0, 0)] * x.ndim
        pad[axis] = (r, r)
        xp = np.pad(x, pad, mode="constant")
        out = np.zeros_like(x)
        n = x.shape[axis]
        for i, w in enumerate(k):
            sl = [slice(None)] * x.ndim
            sl[axis] = slice(i, i + n)
            out += w * xp[tuple(sl)]
        return out

    return conv(conv(a.astype(np.float32), 0), 1)


def value_noise(n, cell, rng):
    g = int(math.ceil(n / cell)) + 3
    small = rng.random((g, g)).astype(np.float32)
    im = Image.fromarray(small, "F").resize((g * cell, g * cell), Image.BICUBIC)
    return np.asarray(im, np.float32)[cell:cell + n, cell:cell + n]


def fbm(n, seed, octaves=((32, 0.5), (16, 0.3), (8, 0.2))):
    rng = np.random.default_rng(seed)
    tot = sum(w for _, w in octaves)
    out = sum(w * value_noise(n, c, rng) for c, w in octaves) / tot
    return np.clip(out, 0, 1)


class Shapes:
    """Anti-aliased shape masks (float 0..1) drawn with PIL at `ss`x supersampling."""

    def __init__(self, n=N, ss=3):
        self.n, self.ss = n, ss

    def _new(self):
        im = Image.new("L", (self.n * self.ss, self.n * self.ss), 0)
        return im, ImageDraw.Draw(im)

    def _fin(self, im, b):
        arr = np.asarray(im.resize((self.n, self.n), Image.BOX), np.float32) / 255.0
        return blur(arr, b) if b else arr

    def _s(self, pts):
        return [(x * self.ss, y * self.ss) for x, y in pts]

    def poly(self, pts, b=0.0):
        im, d = self._new()
        d.polygon(self._s(pts), fill=255)
        return self._fin(im, b)

    def polys(self, lst, b=0.0):
        im, d = self._new()
        for pts in lst:
            d.polygon(self._s(pts), fill=255)
        return self._fin(im, b)

    def ellipse(self, cx, cy, rx, ry=None, b=0.0):
        ry = rx if ry is None else ry
        im, d = self._new()
        s = self.ss
        d.ellipse([(cx - rx) * s, (cy - ry) * s, (cx + rx) * s, (cy + ry) * s], fill=255)
        return self._fin(im, b)

    def circles(self, lst, b=0.0):
        im, d = self._new()
        s = self.ss
        for c in lst:
            cx, cy, rx = c[:3]
            ry = c[3] if len(c) > 3 else rx
            d.ellipse([(cx - rx) * s, (cy - ry) * s, (cx + rx) * s, (cy + ry) * s], fill=255)
        return self._fin(im, b)

    def line(self, pts, w, b=0.0):
        im, d = self._new()
        s = self.ss
        d.line(self._s(pts), fill=255, width=max(1, int(w * s)), joint="curve")
        for x, y in (pts[0], pts[-1]):
            d.ellipse([(x - w / 2) * s, (y - w / 2) * s, (x + w / 2) * s, (y + w / 2) * s], fill=255)
        return self._fin(im, b)

    def lines(self, lst, w, b=0.0):
        im, d = self._new()
        s = self.ss
        for pts in lst:
            d.line(self._s(pts), fill=255, width=max(1, int(w * s)), joint="curve")
        return self._fin(im, b)


def taper_stroke(pts, w0, w1):
    """Polygon for a stroke along a polyline whose width tapers from w0 to w1."""
    n = len(pts)
    left, right = [], []
    for i, (x, y) in enumerate(pts):
        if i == 0:
            dx, dy = pts[1][0] - x, pts[1][1] - y
        elif i == n - 1:
            dx, dy = x - pts[i - 1][0], y - pts[i - 1][1]
        else:
            dx, dy = pts[i + 1][0] - pts[i - 1][0], pts[i + 1][1] - pts[i - 1][1]
        ln = math.hypot(dx, dy) or 1
        nx, ny = -dy / ln, dx / ln
        w = (w0 + (w1 - w0) * i / (n - 1)) / 2
        left.append((x + nx * w, y + ny * w))
        right.append((x - nx * w, y - ny * w))
    return left + right[::-1]


def arc_pts(cx, cy, r, a0, a1, steps=24, rscale=1.0):
    """points along an arc (degrees), radius may grow by rscale over the arc (spirals)."""
    out = []
    for i in range(steps + 1):
        t = i / steps
        a = math.radians(a0 + (a1 - a0) * t)
        rr = r * (1 + (rscale - 1) * t)
        out.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    return out


def _c(c):
    return col(c) if isinstance(c, str) else np.asarray(c, np.float32)


class Canvas:
    """Premultiplied-alpha float RGBA canvas."""

    def __init__(self, n, bg=None):
        self.n = n
        self.rgb = np.zeros((n, n, 3), np.float32)
        self.a = np.zeros((n, n), np.float32)
        if bg is not None:
            self.rgb[:] = _c(bg)
            self.a[:] = 1.0

    def over(self, c, m, k=1.0):
        c = _c(c)
        m = np.clip(np.asarray(m, np.float32) * k, 0, 1)
        self.rgb = self.rgb * (1 - m[..., None]) + c * m[..., None]
        self.a = self.a * (1 - m) + m
        return self

    def add(self, c, m, k=1.0):
        c = _c(c)
        m = np.clip(np.asarray(m, np.float32) * k, 0, None)
        self.rgb = self.rgb + c * m[..., None]
        lum = c.max(axis=-1) if c.ndim == 3 else float(c.max())
        self.a = np.clip(self.a + m * lum, 0, 1)
        return self

    def glow(self, m, c, sigma, k=1.0):
        return self.add(c, blur(m, sigma), k)

    def shade(self, f):
        self.rgb = self.rgb * np.asarray(f, np.float32)[..., None]
        return self

    def down(self, size, method=Image.LANCZOS):
        out = Canvas(size)
        ch = [self.rgb[..., i] for i in range(3)] + [self.a]
        res = [np.asarray(Image.fromarray(np.ascontiguousarray(c), "F").resize((size, size), method), np.float32)
               for c in ch]
        out.rgb = np.stack(res[:3], -1)
        out.a = np.clip(res[3], 0, 1)
        out.rgb = np.minimum(np.clip(out.rgb, 0, None), out.a[..., None])
        return out

    def image(self):
        a = self.a[..., None]
        rgb = np.where(a > 1e-4, self.rgb / np.maximum(a, 1e-4), 0)
        arr = np.concatenate([np.clip(rgb, 0, 1), np.clip(a, 0, 1)], -1)
        return Image.fromarray((arr * 255 + 0.5).astype(np.uint8), "RGBA")


def icon_bg(inner, outer, cx=64, cy=64, reach=0.80, power=1.25, seed=0, mottle=0.10):
    """Radial vignette background in the school colour with a little painterly mottling."""
    d = dist(N, cx, cy) / (N * reach)
    t = np.clip(d, 0, 1) ** power
    bg = lerp(col(inner), col(outer), t)
    nz = fbm(N, seed, ((24, 0.6), (10, 0.4)))
    bg = bg * (1 + mottle * (nz - 0.5) * 2)[..., None]
    return Canvas(N, bg)


def finish_icon(cv, contrast=1.12, sat=1.15, sharpen=35, grain=0.015, seed=0, edge=0.80):
    """128 -> 16 downsample + grading. `edge` darkens the outer pixel ring (ISS-style frame vignette)."""
    small = cv.down(16, Image.LANCZOS)
    rgb = np.clip(small.rgb, 0, 1)
    rng = np.random.default_rng(1000 + seed)
    rgb = rgb * (1 + rng.normal(0, grain, (16, 16, 1)).astype(np.float32))
    if edge < 1:
        f = np.ones((16, 16), np.float32)
        f[0, :] = f[-1, :] = f[:, 0] = f[:, -1] = edge
        f[0, 0] = f[0, -1] = f[-1, 0] = f[-1, -1] = edge * 0.9
        rgb = rgb * f[..., None]
    img = Image.fromarray((np.clip(rgb, 0, 1) * 255 + 0.5).astype(np.uint8), "RGB")
    img = ImageEnhance.Contrast(img).enhance(contrast)
    img = ImageEnhance.Color(img).enhance(sat)
    if sharpen:
        img = img.filter(ImageFilter.UnsharpMask(radius=0.7, percent=sharpen, threshold=0))
    return img.convert("RGBA")


def sprite(cv, size, method=Image.BOX, alpha_gamma=1.0):
    """Downsample a transparent composition to a sprite, keeping premultiplied alpha."""
    small = cv.down(size, method)
    if alpha_gamma != 1.0:
        a_new = np.clip(small.a, 0, 1) ** alpha_gamma
        scale = np.where(small.a > 1e-4, a_new / np.maximum(small.a, 1e-4), 0)
        small.rgb *= scale[..., None]
        small.a = a_new
    return small.image()


# ============================================================================ pixel-art helpers
def blank(w=16, h=None):
    return Image.new("RGBA", (w, h or w), (0, 0, 0, 0))


def draw_map(img, rows, pal, ox=0, oy=0):
    """rows: list of strings; each char maps to a palette colour, '.'/' ' are skipped."""
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in pal:
                X, Y = ox + x, oy + y
                if 0 <= X < img.width and 0 <= Y < img.height:
                    img.putpixel((X, Y), pal[ch])


def ramp(*hexes):
    return [hexa(h) for h in hexes]


BAYER4 = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]


def dither_pick(rmp, v, x, y, lo=1):
    """Pick a ramp entry for continuous shade v (0..len-1) with ordered dithering.
    lo = lowest index allowed (0 is usually reserved for outlines)."""
    v = max(lo, min(len(rmp) - 1, v))
    base = int(math.floor(v))
    frac = v - base
    if base + 1 < len(rmp) and frac > (BAYER4[y % 4][x % 4] + 0.5) / 16:
        base += 1
    return rmp[base]


def outline_pass(img, mat, ramps):
    """Selective outline: silhouette pixels take the darkest shade of their own material."""
    w, h = img.size
    px = img.load()
    out = img.copy()
    po = out.load()
    for y in range(h):
        for x in range(w):
            if px[x, y][3] == 0:
                continue
            edge = False
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                X, Y = x + dx, y + dy
                if not (0 <= X < w and 0 <= Y < h) or px[X, Y][3] == 0:
                    edge = True
                    break
            if edge:
                m = mat.get((x, y))
                if m in ramps:
                    po[x, y] = ramps[m][0]
    return out


# ============================================================================ spellbooks
PAGE_R = ramp("#6A5A44", "#B8A47E", "#E4D6B0", "#F6EED4")

BOOKS = {
    "slider_codex": dict(
        cover=ramp("#34342F", "#6E6E68", "#A9A9A0", "#D8D8D0", "#F2F2EC"),
        metal=ramp("#3E220C", "#7E4A1E", "#B87332", "#E3A35A", "#FFD9A0"),
        emblem=ramp("#0E3E7A", "#2A8EE8", "#7FDCFF", "#E8FCFF"),
        ink="#3A7EC0", gilt=None, frame=False, texture="stone", seed=11),
    "valkyrie_grimoire": dict(
        cover=ramp("#4C4C6C", "#A4A6C2", "#D6D8E8", "#F2F3FA", "#FFFFFF"),
        metal=ramp("#5A3E0A", "#8A6414", "#D9A830", "#F4D16A", "#FFF3C4"),
        emblem=ramp("#6A480C", "#D9A830", "#F8DC7A", "#FFFBEA"),
        ink="#B08A2A", gilt="#E8C35A", frame=True, texture="cloth", seed=23),
    "solar_codex": dict(
        cover=ramp("#3A0A04", "#7A1E0E", "#B83A18", "#E0602A", "#FF9A52"),
        metal=ramp("#5A3006", "#9A5E10", "#E09A26", "#FFD25A", "#FFF4B8"),
        emblem=ramp("#A0360A", "#FF9A1A", "#FFE066", "#FFFFFF"),
        ink="#A8341A", gilt="#F2A83A", frame=True, texture="leather", seed=37),
}

# Isometric (2:1) view of a closed book lying flat, like Iron's Spells' own book icons:
# cover edge a runs up-right, edge b runs down-right; the page block shows on the two front faces.
BOOK_EA, BOOK_EB, BOOK_A, BOOK_B, BOOK_H, BOOK_O = (1.0, -0.62), (1.0, 0.62), 8, 7, 4, (0.5, 10.2)


def _solve(X, Y, u, v):
    d = u[0] * v[1] - u[1] * v[0]
    return (X * v[1] - Y * v[0]) / d, (u[0] * Y - u[1] * X) / d


def book_faces():
    """Classify each pixel of the 16x16 book icon: T (cover top), S (front: page fore-edge), P (left: page head)."""
    ea, eb, A, B, H, O = BOOK_EA, BOOK_EB, BOOK_A, BOOK_B, BOOK_H, BOOK_O
    faces = {}
    e = 0.02
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5, y + 0.5
            a, b = _solve(px - O[0], py - (O[1] - H), ea, eb)
            if -e <= a <= A + e and -e <= b <= B + e:
                faces[(x, y)] = ("T", a, b)
                continue
            a, h = _solve(px - O[0] - B * eb[0], py - O[1] - B * eb[1], ea, (0, -1))
            if -e <= a <= A + e and -e <= h <= H + e:
                faces[(x, y)] = ("S", a, h)
                continue
            b, h = _solve(px - O[0], py - O[1], eb, (0, -1))
            if -e <= b <= B + e and -e <= h <= H + e:
                faces[(x, y)] = ("P", b, h)
    return faces


# emblem stamps (screen space, centred on the cover). 1..3 = emblem ramp index, 0 = emblem outline
EMBLEMS = {
    "slider_codex": [  # a glowing eye rune inside a carved lozenge
        "..0000.",
        ".012210",
        "0123321",
        ".012210",
        "..0000.",
    ],
    "valkyrie_grimoire": [  # golden pair of wings
        "1.....1",
        "21...12",
        "3210123",
        ".23332.",
        "..232..",
    ],
    "solar_codex": [  # blazing sun
        ".1.2.1.",
        "..232..",
        "2333332",
        "..232..",
        ".1.2.1.",
    ],
}


# smaller emblems for the isometric item icon (the cover is only ~10px across)
EMBLEMS_SMALL = {
    "slider_codex": [".000.", "02320", ".000."],       # glowing eye
    "valkyrie_grimoire": ["1...1", "21.12", ".232."],  # wings
    "solar_codex": ["1.2.1", ".232.", "1.2.1"],        # sun
}


def book_icon(name, spec):
    F = book_faces()
    A, B, H = BOOK_A, BOOK_B, BOOK_H
    cov, met, emb = spec["cover"], spec["metal"], spec["emblem"]
    pag = PAGE_R
    if spec["gilt"]:
        g = hexa(spec["gilt"])
        pag = [pag[0], tuple(int(g[i] * 0.72) for i in range(3)) + (255,), g,
               tuple(min(255, int(g[i] * 0.4 + 255 * 0.6)) for i in range(3)) + (255,)]
    rng = random.Random(spec["seed"])
    img = blank()
    mat = {}
    px = img.load()

    def put(x, y, c, m):
        px[x, y] = c
        mat[(x, y)] = m

    def kind(x, y):
        f = F.get((x, y))
        return f[0] if f else None

    for (x, y), (f, u, v) in F.items():
        if f == "T":
            a, b = u, v
            ca, cb = min(a, A - a), min(b, B - b)
            if ca / 2.1 + cb / 1.7 < 1.0:  # metal corner caps
                s = 3.3 - 1.4 * ((x + y) / 24.0)
                put(x, y, dither_pick(met, s, x, y), "metal")
                continue
            s = 3.25 - 1.8 * ((x * 0.55 + y) / 17.0)  # light from the top-left
            if spec["texture"] == "stone":
                s += rng.choice((0, 0, 0, -0.5, 0.35))
            elif spec["texture"] == "leather":
                s += rng.choice((0, 0, -0.35, 0.2))
            else:
                s += 0.25 if (x + y) % 2 == 0 else 0
            put(x, y, dither_pick(cov, s, x, y), "cover")
        else:
            a_or_b, h = u, v
            along = u
            length = A if f == "S" else B
            end = min(along, length - along)
            top_cover = h > H - 1.0
            bottom_cover = h < 1.0
            if top_cover or bottom_cover:
                if end < 1.3:
                    put(x, y, met[2] if f == "P" else met[1], "metal")
                else:
                    base = 2.3 if f == "P" else 1.6
                    if bottom_cover:
                        base -= 0.7
                    put(x, y, dither_pick(cov, base, x, y), "cover")
            else:
                shade = 3 if f == "P" else 2
                if f == "S" and (x + y) % 3 == 0:
                    shade -= 1
                put(x, y, pag[shade], "page")

    # cover rim: top-face pixels bordering the side faces catch the light
    for (x, y), (f, u, v) in F.items():
        if f != "T" or mat.get((x, y)) == "metal":
            continue
        below = kind(x, y + 1)
        if below in ("S", "P"):
            put(x, y, cov[4] if not spec["frame"] else met[3], "metal" if spec["frame"] else "cover")

    # frame: a thin inset band (metal trim or carved groove) one pixel inside the top face edge
    ring1 = set()
    for (x, y), (f, u, v) in F.items():
        if f == "T" and any(kind(x + dx, y + dy) != "T" for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            ring1.add((x, y))
    for (x, y), (f, u, v) in F.items():
        if f != "T" or (x, y) in ring1 or mat.get((x, y)) == "metal":
            continue
        if any((x + dx, y + dy) in ring1 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            if spec["frame"]:
                put(x, y, met[2] if (x + y) % 2 else met[3], "metal")
            else:
                put(x, y, cov[1], "cover")

    # clasp strap over the fore-edge, onto the cover
    a_mid = A * 0.5
    for (x, y), (f, u, v) in F.items():
        if f == "S" and abs(u - a_mid) < 0.9:
            put(x, y, met[3] if u < a_mid else met[2], "metal")
        elif f == "T" and abs(u - a_mid) < 0.9 and v > B - 1.3:
            put(x, y, met[3], "metal")

    img = outline_pass(img, mat, {"cover": cov, "metal": met, "page": pag})

    # emblem stamp centred on the cover
    ea, eb, O = BOOK_EA, BOOK_EB, BOOK_O
    cx = O[0] + ea[0] * A / 2 + eb[0] * B / 2
    cy = O[1] - H + ea[1] * A / 2 + eb[1] * B / 2
    rows = EMBLEMS_SMALL[name]
    ox = int(round(cx - len(rows[0]) / 2))
    oy = int(round(cy - len(rows) / 2))
    pal = {"0": emb[0], "1": emb[1], "2": emb[2], "3": emb[3]}
    draw_map(img, rows, pal, ox, oy)
    return img



def book_icon_front(name, spec):
    """Front-facing closed spellbook with visible page block (right and bottom), spine band on the left,
    metal corner caps, optional gilt frame and a centred emblem. Reads clearly at 16x16."""
    cov, met, emb = spec["cover"], spec["metal"], spec["emblem"]
    pag = PAGE_R
    if spec["gilt"]:
        g = hexa(spec["gilt"])
        pag = [pag[0], tuple(int(g[i] * 0.7) for i in range(3)) + (255,), g,
               tuple(min(255, int(g[i] * 0.4 + 255 * 0.6)) for i in range(3)) + (255,)]
    rng = random.Random(spec["seed"])
    img = blank()
    px = img.load()
    x0, y0, x1, y1 = 1, 0, 12, 12          # cover rectangle (inclusive)
    # page block peeking out to the right and below (the book has thickness)
    for y in range(y0 + 2, y1 + 3):
        for x in range(x1 + 1, x1 + 3):
            if y <= y1 + 1:
                px[x, y] = pag[3] if (y % 2 == 0) else pag[2]
    for x in range(x0 + 2, x1 + 3):
        for y in range(y1 + 1, y1 + 3):
            px[x, y] = pag[2] if (x % 2 == 0) else pag[3]
    for y in range(y0 + 2, y1 + 3):
        px[x1 + 3, y] = pag[0]
    for x in range(x0 + 2, x1 + 4):
        px[x, y1 + 3] = pag[0]
    px[x1 + 3, y1 + 3] = (0, 0, 0, 0)
    # cover
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            edge = x in (x0, x1) or y in (y0, y1)
            if edge:
                px[x, y] = cov[0]
                continue
            v = 3.2 - 1.5 * ((x - x0) * 0.45 + (y - y0)) / 13.0
            if spec["texture"] == "stone":
                v += rng.choice((0, 0, 0, 0, -0.5, 0.3))
            elif spec["texture"] == "leather":
                v += rng.choice((0, 0, 0, -0.25, 0.15))
            else:
                v += 0.1 if (x + y) % 4 == 0 else 0
            px[x, y] = dither_pick(cov, v, x, y)
    # spine band on the left
    for y in range(y0 + 1, y1):
        px[x0 + 1, y] = cov[1]
        px[x0 + 2, y] = cov[2] if y % 3 else met[2]
    # gilt / carved frame
    for y in range(y0 + 2, y1 - 1):
        for x in range(x0 + 4, x1 - 1):
            if x in (x0 + 4, x1 - 2) or y in (y0 + 2, y1 - 2):
                px[x, y] = (met[3] if (x + y) % 2 else met[2]) if spec["frame"] else cov[1]
    # metal corner caps
    for (cx, cy, sx, sy) in ((x0 + 3, y0 + 1, 1, 1), (x1 - 1, y0 + 1, -1, 1), (x0 + 3, y1 - 1, 1, -1), (x1 - 1, y1 - 1, -1, -1)):
        px[cx, cy] = met[4]
        px[cx + sx, cy] = met[3]
        px[cx, cy + sy] = met[3]
    # clasp over the right edge
    for y in (y0 + 6, y0 + 7):
        px[x1, y] = met[3] if y == y0 + 6 else met[1]
        px[x1 + 1, y] = met[2]
    # emblem
    rows = EMBLEMS[name]
    ox = (x0 + 4 + x1 - 2) // 2 - len(rows[0]) // 2 + 1
    oy = (y0 + 2 + y1 - 2) // 2 - len(rows) // 2 + 1
    draw_map(img, rows, {"0": emb[0], "1": emb[1], "2": emb[2], "3": emb[3]}, ox, oy)
    return img


def book_icon_iso(name, spec):
    """Isometric closed spellbook matching the viewing angle of Iron's Spells' book icons:
    the cover is a parallelogram L-T-R-F seen from above-front, and the two front faces show a thin
    page block between the top and bottom covers. Metal corner caps, a clasp and a small emblem."""
    cov, met, emb = spec["cover"], spec["metal"], spec["emblem"]
    pag = PAGE_R
    L, T, R, F = (0.5, 6.5), (8.5, 1.5), (15.5, 5.5), (7.5, 10.5)
    H = 4.0

    def inv(px, py, o, u, v):
        """solve p = o + a*u + b*v"""
        return _solve(px - o[0], py - o[1], u, v)

    u_a = (T[0] - L[0], T[1] - L[1])   # back-left edge  (s)
    u_b = (F[0] - L[0], F[1] - L[1])   # front-left edge (t)
    u_c = (R[0] - F[0], R[1] - F[1])   # front-right edge
    down = (0.0, H)
    img = blank()
    px = img.load()
    mat = {}
    rng = random.Random(spec["seed"])

    def put(x, y, c, m):
        px[x, y] = c
        mat[(x, y)] = m

    for y in range(16):
        for x in range(16):
            cx, cy = x + 0.5, y + 0.5
            s_, t_ = inv(cx, cy, L, u_a, u_b)
            if -0.02 <= s_ <= 1.02 and -0.02 <= t_ <= 1.02:
                # ---- cover (top face)
                edge_s = min(s_, 1 - s_) * 8
                edge_t = min(t_, 1 - t_) * 7
                if edge_s < 1.3 and edge_t < 1.3:
                    put(x, y, met[4] if (s_ > 0.5) == (t_ < 0.5) else met[3], "metal")       # corner caps
                elif edge_s < 0.9 or edge_t < 0.9:
                    rim = (met[2] if (x + y) % 2 else met[3]) if spec["frame"] else cov[1]
                    put(x, y, rim, "metal" if spec["frame"] else "cover")
                elif spec["frame"] and (edge_s < 1.8 or edge_t < 1.8):
                    put(x, y, dither_pick(cov, 2.2 - 0.6 * t_, x, y), "cover")
                else:
                    v = 3.4 - 1.5 * t_ - 0.4 * (1 - s_)
                    if spec["texture"] == "stone":
                        v += rng.choice((0, 0, 0, -0.45, 0.25))
                    elif spec["texture"] == "leather":
                        v += rng.choice((0, 0, 0, -0.25, 0.15))
                    put(x, y, dither_pick(cov, v, x, y), "cover")
                continue
            for face, origin, along, length in (("left", L, u_b, 7), ("right", F, u_c, 7)):
                a_, h_ = inv(cx, cy, origin, along, down)
                if -0.02 <= a_ <= 1.02 and -0.02 <= h_ <= 1.02:
                    end = min(a_, 1 - a_) * length
                    lit = face == "left"
                    if h_ < 0.3 or h_ > 0.68:          # top / bottom cover boards
                        if end < 1.2:
                            put(x, y, met[3] if lit else met[2], "metal")
                        else:
                            base = (2 if lit else 1) if h_ < 0.3 else (2 if lit else 1)
                            put(x, y, cov[base], "cover")
                    elif face == "right" and abs(a_ - 0.5) < 0.1:
                        put(x, y, met[3] if h_ < 0.5 else met[2], "metal")        # clasp
                    else:                                  # page block
                        put(x, y, pag[3] if lit else pag[2], "page")
                    break
    # clasp tab reaching onto the cover
    for y in range(16):
        for x in range(16):
            cx, cy = x + 0.5, y + 0.5
            s_, t_ = inv(cx, cy, L, u_a, u_b)
            if 0 <= s_ <= 1 and 0.82 <= t_ <= 1.02:
                # front-right edge parameter along F->R equals s_ here (parallelogram)
                if abs(s_ - 0.5) < 0.08:
                    put(x, y, met[4], "metal")
    img = outline_pass(img, mat, {"cover": cov, "metal": met, "page": pag})
    # emblem centred on the cover
    rows = EMBLEMS_SMALL[name]
    cx, cy = (T[0] + F[0]) / 2, (T[1] + F[1]) / 2
    ox = int(round(cx - len(rows[0]) / 2))
    oy = int(round(cy - len(rows) / 2))
    draw_map(img, rows, {"0": emb[0], "1": emb[1], "2": emb[2], "3": emb[3]}, ox, oy)
    return img

def book_model_texture(name, spec):
    """64x64 texture following Iron's Spells' spellbook template UV layout.
    outer cover x0-10,y9-16 | cover edges x10 & x21 | inner cover x11-21,y9-16 |
    cover spine/fore edges x0-2,y16-26 | spine x0-24,y21-32 (visible strip x10-12) |
    pages x12-32,y16-21 | page fore-edge x20-24,y8-16"""
    cov, met, emb = spec["cover"], spec["metal"], spec["emblem"]
    rng = random.Random(spec["seed"] * 7 + 1)
    img = blank(64)
    px = img.load()

    def P(x, y, c):
        px[x, y] = c

    def tex(x, y, base):
        """cover material texture around ramp index `base`"""
        t = spec["texture"]
        v = base
        if t == "stone":
            v += rng.choice((0, 0, 0, 0.6, -0.8))
        elif t == "leather":
            v += rng.choice((0, 0, -0.5, 0.4))
        else:
            v += 0.35 if (x + y) % 2 == 0 else -0.1
        return dither_pick(cov, v, x, y)

    # ---- outer cover face (10x7). top row = spine side, bottom row = fore-edge side
    emblem_map = {
        "slider_codex": ["..0000..", ".012210.", ".023320.", ".012210.", "..0000.."],
        "valkyrie_grimoire": ["1.0..0.1", "21.00.12", "32100123", ".233332.", "...33..."],
        "solar_codex": ["1..22..1", "..2332..", "23333332", "..2332..", "1..22..1"],
    }[name]
    for fx, mirror in ((0, False),):
        for ly in range(7):
            for lx in range(10):
                x, y = fx + lx, 9 + ly
                shade = 3.1 - 1.5 * ((lx * 0.4 + ly) / 9.0)
                P(x, y, tex(x, y, shade))
        # frame / groove
        for lx in range(10):
            for ly in (0, 6):
                P(fx + lx, 9 + ly, (met[2] if lx % 2 else met[3]) if spec["frame"] else cov[1])
        for ly in range(7):
            for lx in (0, 9):
                P(fx + lx, 9 + ly, (met[2] if ly % 2 else met[3]) if spec["frame"] else cov[1])
        # corner caps (L-shaped with a highlight)
        for (cx, cy, sx, sy) in ((0, 0, 1, 1), (9, 0, -1, 1), (0, 6, 1, -1), (9, 6, -1, -1)):
            for (dx, dy, k) in ((0, 0, 1), (1, 0, 2), (0, 1, 2), (1, 1, 3)):
                P(fx + cx + dx * sx, 9 + cy + dy * sy, met[k + (1 if cy == 0 else 0)] if k < 3 else met[4])
        # emblem (8x5 at x1..8, y10..14)
        pal = {"0": emb[0], "1": emb[1], "2": emb[2], "3": emb[3]}
        draw_map(img, emblem_map, pal, fx + 1, 10)
        # clasp tab on the fore-edge side
        P(fx + 4, 15, met[3])
        P(fx + 5, 15, met[2])

    # ---- inner cover face (lining), x11-21
    for ly in range(7):
        for lx in range(10):
            x, y = 11 + lx, 9 + ly
            v = 1.6 + (0.3 if (lx + ly) % 2 == 0 else 0)
            if lx in (0, 9) or ly in (0, 6):
                v = 1.0
            P(x, y, dither_pick(cov, v, x, y))
    # ---- cover edges (north/south, 1x7)
    for ex in (10, 21):
        for ly in range(7):
            P(ex, 9 + ly, met[2] if ly in (0, 6) else cov[1 + (ly % 2)])
    # ---- cover long edges (1x10 each at x0 and x1, y16-26)
    for ex in (0, 1):
        for ly in range(10):
            P(ex, 16 + ly, met[2] if ly in (0, 9) else cov[2 if (ly + ex) % 2 else 1])

    # ---- spine region x0-24, y21-32 : leather with raised bands
    for y in range(21, 32):
        for x in range(0, 24):
            v = 2.2 if x % 2 == 0 else 1.5  # 2px wide rounded spine: light / dark column
            P(x, y, tex(x, y, v))
    for y in (21, 30):
        for x in range(0, 24):
            P(x, y, met[3] if x % 2 == 0 else met[2])
    for y in (23, 25, 28):
        for x in range(0, 24):
            P(x, y, met[2] if x % 2 == 0 else met[1])
    for x in range(0, 24):
        P(x, 31, cov[1] if x % 2 else met[2])
    # emblem pip on the spine centre
    P(10, 26, emb[2])
    P(11, 26, emb[1])
    P(10, 27, emb[1])
    P(11, 27, emb[0])

    # ---- pages x12-32, y16-21
    ink = hexa(spec["ink"])
    ink_l = tuple(int(ink[i] * 0.5 + PAGE_R[2][i] * 0.5) for i in range(3)) + (255,)
    for y in range(16, 21):
        for x in range(12, 32):
            P(x, y, PAGE_R[3] if (x + y) % 5 else PAGE_R[2])
    for sx in (12, 22):  # open-page surfaces (8x5): lines of rune text
        for x in range(sx, sx + 8):
            P(x, 16, PAGE_R[2])
            P(x, 20, PAGE_R[2])
        for y in (17, 19):
            for x in range(sx + 1, sx + 7):
                if rng.random() < 0.7:
                    P(x, y, ink if rng.random() < 0.6 else ink_l)
        P(sx + 3, 18, ink_l)
    for sx in (20, 30):  # page ends (2x5): stacked page lines
        for y in range(16, 21):
            P(sx, y, PAGE_R[2] if y % 2 else PAGE_R[3])
            P(sx + 1, y, PAGE_R[1] if y % 2 else PAGE_R[2])
    # ---- fore-edge x20-24, y8-16 (gilded where the book has gilt)
    g = hexa(spec["gilt"]) if spec["gilt"] else PAGE_R[3]
    g_d = tuple(int(g[i] * 0.78) for i in range(3)) + (255,)
    for y in range(8, 16):
        for x in range(20, 24):
            P(x, y, g if x % 2 == 0 else g_d)
    for x in range(20, 24):
        P(x, 8, PAGE_R[1])
        P(x, 15, PAGE_R[1])
    return img


# ============================================================================ accessories
GOLD = ramp("#5A3808", "#9A6616", "#D6A030", "#F4CE5C", "#FFF2B8")
SILVER = ramp("#34343F", "#6E6E80", "#A8A8BA", "#DADAE6", "#FFFFFF")
AMBRO = ramp("#8A6410", "#D2AE32", "#F6E27A", "#FFF7B8", "#FFFFFF")
ZANITE = ramp("#2A0F4A", "#4B2476", "#8E4BC8", "#C58AF0", "#F4E4FF")
GRAV = ramp("#45184A", "#6F2F73", "#C667C6", "#F0B0F0", "#FFE8FF")
LEATHER = ramp("#1A1020", "#332238", "#4E3656", "#6C5078", "#8C6E98")
CLOTH = ramp("#48486A", "#9C9EBE", "#CDD0E4", "#EEF0F8", "#FFFFFF")
VGOLD = ramp("#5E3E08", "#8A6414", "#E3B53A", "#F8D878", "#FFF3C4")


def ambrosium_ring():
    img = blank()
    mat = {}
    px = img.load()
    cx, cy = 7.5, 10.0
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            outer = (dx / 6.6) ** 2 + (dy / 5.2) ** 2
            inner = (dx / 3.4) ** 2 + ((dy + 0.9) / 2.4) ** 2
            if outer <= 1.0 and inner > 1.0:
                ang = math.atan2(dy, dx)
                # outer surface lit from the top-left; the inner back wall (top of the hole) is shadowed
                light = -math.cos(ang - math.radians(-135))
                v = 2.3 + 1.2 * light
                if dy < -0.5 and inner < 1.9:
                    v = 1.3 + 0.4 * light  # inner wall seen through the hole
                if dy > 0.5 and inner < 1.7:
                    v = 3.4  # lower inner lip catches light
                px[x, y] = dither_pick(GOLD, v, x, y)
                mat[(x, y)] = "gold"
    img = outline_pass(img, mat, {"gold": GOLD})
    px = img.load()
    for (x, y) in ((3, 8), (4, 7), (2, 10)):  # specular glints
        px[x, y] = GOLD[4]
    # crystal setting on top: prongs + tall faceted ambrosium crystal
    g = {"o": GOLD[0], "d": GOLD[1], "m": GOLD[2], "l": GOLD[3]}
    a = {"O": AMBRO[0], "D": AMBRO[1], "M": AMBRO[2], "L": AMBRO[3], "W": AMBRO[4]}
    pal = dict(g, **a)
    rows = [
        "....OO....",
        "...OWLO...",
        "..OWLMDO..",
        "..OLLMDO..",
        ".OWLMMDDO.",
        ".OLLMMDDO.",
        "odOLMMDOdo",
        "omlOOOOOmo",
        ".oddmmddo.",
    ]
    draw_map(img, rows, pal, 3, -1)
    return img


def zanite_pendant():
    img = blank()
    pal = {
        "a": SILVER[3], "b": SILVER[1], "s": SILVER[2], "S": SILVER[4], "k": SILVER[0],
        "o": ZANITE[0], "d": ZANITE[1], "m": ZANITE[2], "l": ZANITE[3], "W": ZANITE[4],
    }
    rows = [
        ".ababababababab.",   # top of the chain loop, joining both strands
        "..ab........ba..",
        "...ab......ba...",
        "....ab....ba....",
        ".....ab..ba.....",
        "......kaak......",
        ".....ksSask.....",
        "....oookkooo....",
        "...olWllmmmdo...",
        "..olWlllmmmddo..",
        "..ollllmmmdddo..",
        "...ollmmmdddo...",
        "....olmmmddo....",
        ".....olmddo.....",
        "......omdo......",
        ".......oo.......",
    ]
    draw_map(img, rows, pal)
    # facet lines
    for (x, y) in ((7, 9), (7, 10), (8, 11), (8, 12)):
        img.putpixel((x, y), ZANITE[2])
    for (x, y) in ((5, 8), (4, 9)):
        img.putpixel((x, y), ZANITE[4])
    return img


def gravitite_gloves():
    img = blank()
    pal = {
        "o": LEATHER[0], "d": LEATHER[1], "m": LEATHER[2], "l": LEATHER[3], "h": LEATHER[4],
        "O": GRAV[0], "D": GRAV[1], "M": GRAV[2], "L": GRAV[3], "W": GRAV[4],
        "s": hexa("#FFFFFF"), "t": hexa("#F0B0F0", 255),
    }
    rows = [
        ".............t..",
        "......oo....tst.",
        "...oo.lmo.oo.t..",
        "..olmolmdolmo...",
        "..olmdlmdlmdoo..",
        "..olmdlmdlmdlmo.",
        "..olmdlmdlmdlmo.",
        "..oLMdLMdLMdLDo.",
        "oo.oDdoDdoDdODo.",
        "olm.lmmOOmmmmdo.",
        "olmllmOLMOmmmdo.",
        ".olmmmOMDOmmddo.",
        "..olmmmOOmmmdo..",
        "..oOOOOOOOOOOo..",
        "..oMLMMMMMMMDo..",
        "..ooooooooooooo.",
    ]
    draw_map(img, rows, pal)
    return img


def valkyrie_mantle():
    img = blank()
    pal = {
        "o": CLOTH[0], "d": CLOTH[1], "s": CLOTH[2], "w": CLOTH[3], "W": CLOTH[4],
        "G": VGOLD[0], "g": VGOLD[1], "Y": VGOLD[2], "y": VGOLD[3], "Z": VGOLD[4],
        "b": hexa("#6FC8F0"), "B": hexa("#D8F4FF"),
    }
    rows = [
        "................",
        ".....GGGGGG.....",
        "....GyZZZZyG....",
        "...GgYYbBYYgG...",
        "..owWYYbbYYwso..",
        "..owWwwYYwwsdo..",
        ".owWWwwwwwwsddo.",
        ".owWwwswwwwsdso.",
        ".owWwwswwYwsdso.",
        "owWWwwsdwyYsdsdo",
        "owWwwwsdyZywdsdo",
        "owWwwssdwYwwsddo",
        "oGyYgYyYgYyYgYGo",
        "GYy.GyZG.GyZ.GYG",
        "GG...GG...GG..GG",
        "................",
    ]
    draw_map(img, rows, pal)
    return img

# ============================================================================ spell icons
# All icons are composed on a 128x128 canvas (1 final pixel = 8 canvas pixels, so keep shapes bold)
# and finished with finish_icon(). Light comes from the top-left.
S = Shapes()
XS, YS = grid(N)


def vgrad(y0, y1, stops):
    """Vertical colour gradient (per-pixel rgb) from y0 to y1."""
    return ramp_map((YS - y0) / max(y1 - y0, 1), stops)


def dgrad(stops, angle=45, span=128):
    """Diagonal gradient; angle in degrees measured from +x towards +y."""
    a = math.radians(angle)
    t = ((XS - 64) * math.cos(a) + (YS - 64) * math.sin(a)) / span + 0.5
    return ramp_map(t, stops)


def radial(cx, cy, r, stops):
    return ramp_map(dist(N, cx, cy) / r, stops)


def sparkle_mask(cx, cy, r, w=None, b=0.6):
    """Four-point star."""
    w = w or r * 0.22
    return S.polys([[(cx - r, cy), (cx, cy - w), (cx + r, cy), (cx, cy + w)],
                    [(cx, cy - r), (cx + w, cy), (cx, cy + r), (cx - w, cy)]], b)


def cloud_mask(cx, cy, scale=1.0, b=1.2):
    s = scale
    return S.circles([(cx - 26 * s, cy + 4 * s, 16 * s), (cx - 6 * s, cy - 8 * s, 21 * s), (cx + 18 * s, cy - 2 * s, 18 * s),
                      (cx + 32 * s, cy + 8 * s, 12 * s), (cx + 2 * s, cy + 10 * s, 20 * s), (cx - 34 * s, cy + 12 * s, 10 * s)], b)


def icon_aercloud_step():
    cv = icon_bg("#9ADBFF", "#123C80", cy=60, seed=1)
    cv.glow(S.ellipse(64, 92, 52, 18), "#E8F8FF", 10, 0.55)
    cloud = cloud_mask(62, 90, 1.25)
    cv.over(vgrad(62, 118, [(0, "#FFFFFF"), (0.55, "#E4F2FF"), (1, "#8CB8E8")]), cloud)
    cv.over("#FFFFFF", cloud_mask(56, 84, 0.8, 2) * 0.55)
    # a pair of boots stepping onto it
    boots = S.polys([[(34, 44), (50, 44), (50, 64), (60, 70), (60, 76), (34, 76)],
                     [(70, 34), (86, 34), (86, 54), (96, 60), (96, 66), (70, 66)]])
    cv.over(vgrad(34, 76, [(0, "#3A5680"), (1, "#1C2C4A")]), boots)
    cv.over("#7FA6D8", S.polys([[(36, 46), (40, 46), (40, 70), (36, 70)], [(72, 36), (76, 36), (76, 60), (72, 60)]]) * 0.8)
    cv.glow(boots, "#9ADBFF", 4, 0.25)
    for x, y, r in ((24, 30, 9), (104, 88, 7), (100, 24, 6)):
        m = sparkle_mask(x, y, r)
        cv.over("#FFFFFF", m)
        cv.glow(m, "#BFE9FF", 3, 0.8)
    return finish_icon(cv, seed=1)


def icon_stonebreaker_shard():
    cv = icon_bg("#A7B88E", "#18231A", cx=70, cy=58, seed=2)
    # motion streaks behind the shard
    for off, w in ((-14, 5), (0, 7), (14, 5)):
        streak = S.line([(18 + off * 0.3, 112 - off), (58 + off * 0.2, 72 - off)], w, 2)
        cv.add("#E6F2D8", streak, 0.35)
    shard = [(46, 96), (60, 60), (82, 30), (104, 16), (98, 42), (78, 72), (58, 100)]
    body = S.poly(shard)
    cv.glow(body, "#8FEA7A", 9, 0.55)
    cv.over(dgrad([(0.25, "#FFFFFA"), (0.55, "#D2D2C8"), (0.85, "#7A7A72")], 45, 110), body)
    # dark lower facet
    cv.over("#8C8C84", S.poly([(58, 100), (78, 72), (98, 42), (104, 16), (90, 44), (70, 76)]), 0.85)
    cv.over("#FFFFFF", S.line([(56, 70), (80, 40), (98, 24)], 3.5, 0.8), 0.9)
    for pts in ([(26, 50), (36, 44), (38, 56)], [(96, 88), (108, 84), (104, 98)], [(20, 90), (28, 86), (30, 96)], [(72, 108), (80, 104), (80, 114)]):
        chip = S.poly(pts)
        cv.over("#C8C8BE", chip)
        cv.over("#6E6E66", S.poly([pts[1], pts[2], ((pts[0][0] + pts[2][0]) / 2, (pts[0][1] + pts[2][1]) / 2)]))
    return finish_icon(cv, seed=2)


def icon_thunder_crystal():
    cv = icon_bg("#3448B8", "#05081E", seed=3)
    gem = [(64, 14), (92, 60), (64, 114), (36, 60)]
    m = S.poly(gem)
    cv.glow(m, "#FFE27A", 16, 0.9)
    cv.over("#F0B830", m)
    cv.over("#FFF2A6", S.poly([(64, 14), (64, 114), (36, 60)]))       # lit left half
    cv.over("#FFFBE0", S.poly([(64, 14), (50, 60), (64, 60)]))        # top-left facet highlight
    cv.over("#D08E14", S.poly([(64, 60), (92, 60), (64, 114)]), 0.8)  # shaded lower right
    cv.over("#FFFFFF", S.line([(58, 30), (50, 56)], 3, 0.5), 0.9)
    # crackling arcs
    for pts in ([(34, 62), (22, 52), (26, 42), (12, 30)], [(94, 58), (106, 70), (100, 80), (116, 94)],
                [(40, 92), (28, 100), (32, 110)], [(88, 30), (100, 22), (98, 12)]):
        arc = S.line(pts, 3.2, 0.6)
        cv.glow(arc, "#7FE8FF", 4, 0.9)
        cv.over("#E8FFFF", arc)
    return finish_icon(cv, seed=3, sat=1.2)


def icon_frostbound_crystal():
    cv = icon_bg("#86E8FF", "#07304E", seed=4)
    for ang in range(0, 360, 45):
        a = math.radians(ang + 22)
        r0, r1 = 34, 58 if ang % 90 == 0 else 50
        tip = (64 + math.cos(a) * r1, 64 + math.sin(a) * r1)
        side = math.radians(ang + 22 + 90)
        base1 = (64 + math.cos(a) * r0 + math.cos(side) * 6, 64 + math.sin(a) * r0 + math.sin(side) * 6)
        base2 = (64 + math.cos(a) * r0 - math.cos(side) * 6, 64 + math.sin(a) * r0 - math.sin(side) * 6)
        shard = S.poly([base1, tip, base2])
        cv.over("#D8F8FF", shard)
        cv.glow(shard, "#A8F0FF", 3, 0.4)
    orb = S.ellipse(64, 64, 34)
    cv.glow(orb, "#CFF6FF", 14, 0.8)
    cv.over(radial(52, 50, 44, [(0, "#FFFFFF"), (0.35, "#CDF5FF"), (0.75, "#58B8E8"), (1, "#1E5A9A")]), orb)
    cv.over("#FFFFFF", S.ellipse(50, 48, 9, 7, 1.5), 0.9)
    cv.over("#FFFFFF", S.line([(70, 78), (80, 70)], 3, 0.8), 0.5)
    return finish_icon(cv, seed=4)


def icon_summon_fire_minion():
    cv = icon_bg("#C8421A", "#1A0302", cy=70, seed=5)
    body = S.polys([
        [(64, 112), (38, 104), (32, 80), (40, 60), (50, 72), (54, 50), (64, 64), (74, 50), (78, 72), (88, 60), (96, 80), (90, 104)],
        [(40, 70), (22, 58), (26, 76), (38, 84)],   # arms as flame tongues
        [(88, 70), (106, 58), (102, 76), (90, 84)],
    ])
    head = S.polys([[(64, 16), (80, 34), (82, 48), (64, 60), (46, 48), (48, 32)]])
    fig = np.maximum(body, head)
    cv.glow(fig, "#FF8A2A", 14, 0.9)
    cv.over(vgrad(16, 112, [(0, "#FFD04A"), (0.5, "#FF8A1A"), (1, "#C2300A")]), fig)
    core = np.maximum(S.poly([(64, 104), (48, 92), (50, 74), (64, 84), (78, 74), (80, 92)], 2), S.ellipse(64, 44, 11, 10, 2))
    cv.over("#FFF4B0", core, 0.9)
    eyes = S.polys([[(54, 40), (61, 42), (60, 47), (53, 45)], [(74, 40), (67, 42), (68, 47), (75, 45)]])
    cv.over("#3A0600", eyes)
    for x, y in ((20, 30), (106, 26), (100, 104), (26, 108)):
        cv.glow(S.ellipse(x, y, 3), "#FFB04A", 3, 1.2)
    return finish_icon(cv, seed=5, sat=1.1)


def icon_summon_moa():
    cv = icon_bg("#AEE0FF", "#18467E", seed=6)
    legs = S.lines([[(54, 90), (50, 112)], [(70, 90), (74, 112)]], 5)
    cv.over("#E8A030", legs)
    body = S.ellipse(56, 76, 34, 22)
    cv.over(vgrad(54, 98, [(0, "#5A9AF0"), (0.6, "#2E62C0"), (1, "#1C3E86")]), body)
    belly = S.ellipse(62, 84, 22, 12, 2)
    cv.over("#DCEBFF", belly, 0.9)
    wing = S.poly([(28, 66), (62, 58), (74, 72), (40, 86)], 1)
    cv.over("#8CC0FF", wing)
    cv.over("#FFFFFF", S.lines([[(36, 70), (60, 64)], [(38, 78), (64, 72)]], 2.5, 0.6), 0.7)
    neck = S.poly([(72, 70), (84, 38), (98, 40), (88, 76)])
    cv.over("#3E7AD8", neck)
    head = S.ellipse(92, 34, 14, 12)
    cv.over(radial(86, 28, 18, [(0, "#8CC4FF"), (1, "#2E62C0")]), head)
    beak = S.poly([(102, 30), (122, 36), (102, 42)])
    cv.over("#FFC23A", beak)
    cv.over("#C07810", S.poly([(102, 37), (122, 36), (102, 42)]))
    cv.over("#101018", S.ellipse(94, 31, 3.5))
    cv.over("#FFFFFF", S.ellipse(93, 30, 1.2))
    cv.glow(np.maximum(body, head), "#DFF3FF", 6, 0.25)
    return finish_icon(cv, seed=6)


def icon_gravitite_surge():
    cv = icon_bg("#9C3AAE", "#10021A", cy=76, seed=7)
    ground = S.poly([(0, 116), (40, 108), (70, 114), (104, 106), (128, 112), (128, 128), (0, 128)])
    cv.over("#3A1E40", ground)
    for x, y, r in ((30, 96, 6), (60, 102, 5), (92, 96, 6), (46, 84, 4), (80, 88, 4), (104, 80, 4)):
        cv.glow(S.ellipse(x, y, r * 0.6), "#F0A8F0", 4, 1.0)
    for off in (-30, 0, 30):
        cv.add("#E890F0", S.line([(64 + off, 112), (64 + off * 0.9, 70)], 4, 3), 0.35)

    def chunk(cx, cy, r, rot):
        pts = [(cx + math.cos(math.radians(a + rot)) * r * (1 if i % 2 else 0.8), cy + math.sin(math.radians(a + rot)) * r * (1 if i % 2 else 0.8))
               for i, a in enumerate(range(0, 360, 60))]
        m = S.poly(pts)
        cv.glow(m, "#F08AF0", 8, 0.7)
        cv.over(dgrad([(0.3, "#FFD0FF"), (0.55, "#D070D6"), (0.85, "#6F2F73")], 45, r * 3.2), m)
        cv.over("#FFFFFF", S.line([(cx - r * 0.5, cy - r * 0.2), (cx - r * 0.1, cy - r * 0.6)], 3, 0.6), 0.8)

    chunk(40, 60, 18, 10)
    chunk(88, 44, 22, 35)
    chunk(64, 22, 12, 0)
    return finish_icon(cv, seed=7, sat=1.15)


def icon_valkyrie_lunge():
    cv = icon_bg("#FFD76A", "#4E2A02", cx=56, cy=70, seed=8)
    wing = S.polys([[(18, 92), (30, 56), (52, 34), (62, 46), (52, 70), (40, 94)],
                    [(26, 98), (44, 74), (58, 58), (66, 70), (54, 92)]], 1)
    cv.glow(wing, "#FFFFFF", 8, 0.5)
    cv.over(vgrad(34, 98, [(0, "#FFFFFF"), (0.6, "#FFF1C8"), (1, "#E0B85A")]), wing)
    cv.over("#C89A3A", S.lines([[(36, 66), (52, 44)], [(34, 80), (54, 60)], [(40, 92), (60, 72)]], 2.5, 0.5), 0.8)
    for off in (-10, 4, 18):
        cv.add("#FFF6D8", S.line([(8, 110 + off * 0.2), (46, 72 + off)], 3, 2), 0.35)
    lance = S.poly([(30, 106), (104, 32), (118, 12), (98, 26), (24, 100)])
    cv.glow(lance, "#FFE28A", 7, 0.7)
    cv.over(dgrad([(0.3, "#FFF6C8"), (0.6, "#E8B838"), (0.9, "#8A5A0A")], -45, 90), lance)
    tip = S.poly([(92, 44), (118, 12), (86, 38)])
    cv.over("#FFFFFF", tip, 0.9)
    guard = S.poly([(40, 84), (52, 96), (48, 100), (36, 88)])
    cv.over("#8A6414", guard)
    return finish_icon(cv, seed=8)


def icon_zephyr_blast():
    cv = icon_bg("#D6F2FF", "#1C548E", cx=76, seed=9)
    for i, (y, w) in enumerate(((44, 5), (64, 7), (84, 5))):
        gust = S.line(arc_pts(40, y + 30, 34, 200, 290, 16), w, 1.5)
        cv.over("#FFFFFF", gust, 0.75)
    orb = S.ellipse(80, 62, 30)
    cv.glow(orb, "#FFFFFF", 12, 0.6)
    cv.over(radial(70, 52, 40, [(0, "#FFFFFF"), (0.6, "#E4F4FF"), (1, "#8CC2EC")]), orb)
    spiral = S.line(arc_pts(80, 62, 6, 0, 540, 40, 4.0), 4.5, 0.6)
    cv.over("#6FA8DA", spiral, 0.85)
    puff = cloud_mask(98, 98, 0.45, 1)
    cv.over("#F4FAFF", puff, 0.9)
    return finish_icon(cv, seed=9)


def icon_aether_whirlwind():
    cv = icon_bg("#86E6CC", "#07302C", seed=10)
    rings = []
    for i in range(7):
        t = i / 6
        cy = 26 + t * 76
        rx = 42 - t * 30
        cx = 64 + math.sin(t * 3.2) * 8
        rings.append((cx, cy, rx, 7 - t * 2))
    funnel = S.polys([[(rings[0][0] - rings[0][2], rings[0][1]), (rings[0][0] + rings[0][2], rings[0][1]),
                       (rings[-1][0] + rings[-1][2], rings[-1][1]), (rings[-1][0] - rings[-1][2], rings[-1][1])]], 3)
    cv.glow(funnel, "#DFFFF6", 10, 0.5)
    cv.over("#CFF6EC", funnel, 0.75)
    for cx, cy, rx, ry in rings:
        band = S.ellipse(cx, cy, rx, ry, 0.8) - S.ellipse(cx, cy - 2, rx - 5, max(ry - 2, 1), 0.8)
        cv.over("#FFFFFF", np.clip(band, 0, 1), 0.9)
    cv.over("#6FC8B0", S.line([(40, 34), (74, 58), (56, 82), (66, 100)], 3, 1), 0.6)
    for x, y in ((22, 90), (100, 72), (30, 50), (104, 36)):
        cv.over("#E4FFF4", S.ellipse(x, y, 3.5))
    cv.over("#3A6A40", S.poly([(92, 96), (100, 92), (102, 100)]))
    return finish_icon(cv, seed=10)


def icon_solar_flare():
    cv = icon_bg("#FF8A2A", "#300400", seed=11)
    rays = []
    for i in range(12):
        a = math.radians(i * 30 + 8)
        r1 = 58 if i % 2 == 0 else 46
        w = 0.20
        rays.append([(64 + math.cos(a - w) * 26, 64 + math.sin(a - w) * 26), (64 + math.cos(a) * r1, 64 + math.sin(a) * r1),
                     (64 + math.cos(a + w) * 26, 64 + math.sin(a + w) * 26)])
    rm = S.polys(rays, 1)
    cv.glow(rm, "#FFB040", 8, 0.8)
    cv.over(radial(64, 64, 58, [(0.4, "#FFE890"), (1, "#FF7A18")]), rm)
    core = S.ellipse(64, 64, 28)
    cv.glow(core, "#FFE070", 16, 1.1)
    cv.over(radial(56, 56, 34, [(0, "#FFFFFF"), (0.45, "#FFF0A0"), (0.85, "#FFB030"), (1, "#E86A10")]), core)
    flare = S.line(arc_pts(64, 64, 36, 200, 260, 12), 4, 1)
    cv.add("#FFD070", flare, 0.9)
    return finish_icon(cv, seed=11, sat=1.1)


def icon_cloud_sentinels():
    cv = icon_bg("#A4DAFF", "#163C78", seed=12)
    crystal = S.poly([(64, 20), (76, 54), (64, 96), (52, 54)])
    cv.glow(crystal, "#9AE4FF", 10, 0.9)
    cv.over(dgrad([(0.3, "#FFFFFF"), (0.55, "#A8E8FF"), (0.9, "#3A8ED8")], 60, 80), crystal)
    cv.over("#FFFFFF", S.line([(62, 32), (58, 54)], 3, 0.5), 0.9)
    for cx, flip in ((28, 1), (100, -1)):
        c = cloud_mask(cx, 76, 0.55, 1)
        cv.over(vgrad(60, 96, [(0, "#FFFFFF"), (1, "#B8D4F0")]), c)
        for ex in (-6, 6):
            eye = S.ellipse(cx + ex + flip * 2, 74, 3, 4)
            cv.over("#16305A", eye)
            cv.glow(eye, "#FFE070", 2, 0.4)
    for x, y in ((40, 36), (90, 30), (64, 112)):
        m = sparkle_mask(x, y, 6)
        cv.over("#FFFFFF", m)
    return finish_icon(cv, seed=12)


SPELL_ICONS = {
    "aercloud_step": icon_aercloud_step,
    "stonebreaker_shard": icon_stonebreaker_shard,
    "thunder_crystal": icon_thunder_crystal,
    "frostbound_crystal": icon_frostbound_crystal,
    "summon_fire_minion": icon_summon_fire_minion,
    "summon_moa": icon_summon_moa,
    "gravitite_surge": icon_gravitite_surge,
    "valkyrie_lunge": icon_valkyrie_lunge,
    "zephyr_blast": icon_zephyr_blast,
    "aether_whirlwind": icon_aether_whirlwind,
    "solar_flare": icon_solar_flare,
    "cloud_sentinels": icon_cloud_sentinels,
}


# ============================================================================ particles & entity sprites
def premul_image(cv):
    """RGB premultiplied by alpha: required for textures drawn with additive blending (RenderType.eyes)."""
    a = np.clip(cv.a, 0, 1)[..., None]
    rgb = np.clip(cv.rgb, 0, 1)
    rgb = np.minimum(rgb, a)
    arr = np.concatenate([rgb, a], -1)
    return Image.fromarray((arr * 255 + 0.5).astype(np.uint8), "RGBA")


def soft_sprite(n, draw, size, premultiplied=False):
    """Compose on an n x n transparent canvas with draw(canvas, shapes, xs, ys) and downsample."""
    cv = Canvas(n)
    draw(cv, Shapes(n), *grid(n))
    small = cv.down(size, Image.BOX)
    return premul_image(small) if premultiplied else small.image()


def feather_frames():
    frames = []
    for rot in (-35, 10, 55, 100):
        def draw(cv, sh, xs, ys, rot=rot):
            def R(x, y):
                a = math.radians(rot)
                x, y = x - 32, y - 32
                return (32 + x * math.cos(a) - y * math.sin(a), 32 + x * math.sin(a) + y * math.cos(a))
            vane = sh.poly([R(32, 6), R(44, 18), R(42, 42), R(33, 56), R(22, 40), R(22, 18)])
            cv.over("#F4F6FA", vane)
            cv.over("#C8CCD8", sh.poly([R(32, 6), R(33, 56), R(42, 42), R(44, 18)]), 0.8)
            cv.over("#8A8E9C", sh.line([R(32, 8), R(33, 62)], 3))
        frames.append(soft_sprite(64, draw, 8))
    return frames


def sparkle_frames():
    frames = []
    for scale in (0.45, 0.75, 1.0, 0.6):
        def draw(cv, sh, xs, ys, scale=scale):
            r = 30 * scale
            star = sh.polys([[(32 - r, 32), (32, 32 - r * 0.2), (32 + r, 32), (32, 32 + r * 0.2)],
                             [(32, 32 - r), (32 + r * 0.2, 32), (32, 32 + r), (32 - r * 0.2, 32)]], 0.8)
            cv.glow(star, "#FFC83A", 5, 1.6)
            cv.over("#FFFFFF", star)
            cv.glow(sh.ellipse(32, 32, 5 * scale + 2), "#FFFFFF", 3, 1.0)
        frames.append(soft_sprite(64, draw, 8))
    return frames


def cloud_puff_frames():
    frames = []
    for i in range(4):
        def draw(cv, sh, xs, ys, i=i):
            rng = random.Random(40 + i)
            circles = [(64 + rng.uniform(-18, 18), 64 + rng.uniform(-14, 14), rng.uniform(20, 30) * (1 + i * 0.08)) for _ in range(6)]
            m = sh.circles(circles, 8)
            if i:
                nz = fbm(128, 60 + i, ((24, 0.6), (12, 0.4)))
                m = m * np.clip(1.25 - i * 0.28 - (1 - nz) * (0.3 + 0.25 * i), 0, 1)
            cv.over("#FFFFFF", m, 0.95 - 0.12 * i)
            cv.over("#D6E2F0", m * np.clip((ys - 64) / 60, 0, 1), 0.5)
        frames.append(soft_sprite(128, draw, 16))
    return frames


def gravity_mote_frames():
    frames = []
    for i, rot in enumerate((0, 22, 45, 67)):
        def draw(cv, sh, xs, ys, rot=rot, i=i):
            a = math.radians(rot)
            r = 20 + (2 if i % 2 else 0)
            pts = [(32 + math.cos(a + k * math.pi / 2) * r * (1 if k % 2 == 0 else 0.6),
                    32 + math.sin(a + k * math.pi / 2) * r * (1 if k % 2 == 0 else 0.6)) for k in range(4)]
            m = sh.poly(pts, 0.6)
            cv.glow(m, "#F070F0", 5, 1.0)
            cv.over("#F6B8F6", m)
            cv.over("#FFFFFF", sh.ellipse(30, 30, 5, 5, 1), 0.9)
        frames.append(soft_sprite(64, draw, 8))
    return frames


def thunder_crystal_sprite():
    def draw(cv, sh, xs, ys):
        m = sh.poly([(64, 8), (96, 64), (64, 120), (32, 64)])
        cv.glow(m, "#FFE27A", 10, 0.8)
        cv.over("#F0B830", m)
        cv.over("#FFF2A6", sh.poly([(64, 8), (64, 120), (32, 64)]))
        cv.over("#FFFFFF", sh.poly([(64, 8), (48, 64), (64, 64)]), 0.9)
    return soft_sprite(128, draw, 16)


def glow_sprite():
    def draw(cv, sh, xs, ys):
        d = np.hypot(xs - 64, ys - 64) / 64
        a = np.clip(1 - d, 0, 1) ** 1.5
        cv.rgb = np.stack([np.ones_like(a), np.full_like(a, 0.93), np.full_like(a, 0.72)], -1) * a[..., None]
        cv.a = a
    return soft_sprite(128, draw, 32, premultiplied=True)


def solar_core_sprite():
    def draw(cv, sh, xs, ys):
        d = np.hypot(xs - 64, ys - 64) / 64
        color = ramp_map(d, [(0, "#FFFFFF"), (0.35, "#FFF4B0"), (0.65, "#FFB43A"), (0.9, "#F06A10"), (1, "#C04008")])
        a = np.clip((1 - d) * 3.5, 0, 1) ** 1.2
        cv.rgb = color * a[..., None]
        cv.a = a
    return soft_sprite(128, draw, 32, premultiplied=True)


def solar_rays_sprite():
    def draw(cv, sh, xs, ys):
        rays = []
        for i in range(12):
            ang = math.radians(i * 30)
            r1 = 124 if i % 2 == 0 else 96
            w = 0.22
            rays.append([(128 + math.cos(ang - w) * 20, 128 + math.sin(ang - w) * 20), (128 + math.cos(ang) * r1, 128 + math.sin(ang) * r1),
                         (128 + math.cos(ang + w) * 20, 128 + math.sin(ang + w) * 20)])
        m = sh.polys(rays, 2)
        d = np.hypot(xs - 128, ys - 128) / 128
        fade = np.clip(1.3 - d, 0, 1) * np.clip(d * 5 - 0.3, 0, 1)
        color = ramp_map(d, [(0, "#FFF0A0"), (0.5, "#FFB43A"), (1, "#FF6A10")])
        cv.rgb = color * (m * fade)[..., None]
        cv.a = m * fade
    return soft_sprite(256, draw, 64, premultiplied=True)


def solar_bolt_sprite():
    def draw(cv, sh, xs, ys):
        d = np.hypot(xs - 64, ys - 64) / 60
        a = np.clip(1.2 - d, 0, 1) ** 0.8
        color = ramp_map(d, [(0, "#FFFFFF"), (0.3, "#FFE680"), (0.65, "#FF8A1A"), (1, "#B02A08")])
        cv.rgb = color * a[..., None]
        cv.a = a
    return soft_sprite(128, draw, 16)


def zephyr_orb_sprite():
    def draw(cv, sh, xs, ys):
        d = np.hypot(xs - 64, ys - 64) / 60
        a = np.clip(1.15 - d, 0, 1) ** 0.9
        cv.rgb = ramp_map(d, [(0, "#FFFFFF"), (0.6, "#E6F4FF"), (1, "#9CCAEE")]) * a[..., None]
        cv.a = a * 0.9
        spiral = sh.line(arc_pts(64, 64, 6, 0, 600, 60, 8.5), 7, 1.5)
        cv.over("#8CBCE6", spiral * np.clip(1.1 - d, 0, 1), 0.8)
        cv.over("#FFFFFF", sh.ellipse(52, 50, 12, 9, 3), 0.7)
    return soft_sprite(128, draw, 32)


# ============================================================================ preview + main
def preview():
    cell = 136
    items = [(p, im) for p, im in WRITTEN]
    cols = 10
    rows = (len(items) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell, rows * (cell + 14)), (48, 48, 52, 255))
    d = ImageDraw.Draw(sheet)
    for i, (p, im) in enumerate(items):
        x, y = (i % cols) * cell, (i // cols) * (cell + 14)
        s = 128 // max(im.width, im.height) if im.width <= 128 else 1
        big = im.resize((im.width * s, im.height * s), Image.NEAREST)
        sheet.alpha_composite(big, (x + (cell - big.width) // 2, y + 2))
        d.text((x + 2, y + cell - 2), p.split("/")[-1][:22], fill=(220, 220, 220, 255))
    os.makedirs(os.path.dirname(PREVIEW), exist_ok=True)
    sheet.save(PREVIEW)


def main():
    for name, spec in BOOKS.items():
        save(book_icon_iso(name, spec), "item", name + ".png")
        save(book_model_texture(name, spec), "item", "spell_book_models", name + ".png")
    save(ambrosium_ring(), "item", "ambrosium_ring.png")
    save(zanite_pendant(), "item", "zanite_focus_pendant.png")
    save(gravitite_gloves(), "item", "gravitite_casting_gloves.png")
    save(valkyrie_mantle(), "item", "valkyrie_mantle.png")
    for name, fn in SPELL_ICONS.items():
        save(fn(), "gui", "spell_icons", name + ".png")
    for name, frames in (("feather", feather_frames()), ("sky_sparkle", sparkle_frames()),
                         ("cloud_puff", cloud_puff_frames()), ("gravity_mote", gravity_mote_frames())):
        for i, f in enumerate(frames):
            save(f, "particle", f"{name}_{i}.png")
    save(thunder_crystal_sprite(), "entity", "thunder_crystal.png")
    save(glow_sprite(), "entity", "thunder_glow.png")
    save(solar_core_sprite(), "entity", "solar_core.png")
    save(solar_rays_sprite(), "entity", "solar_rays.png")
    save(solar_bolt_sprite(), "entity", "solar_bolt.png")
    save(zephyr_orb_sprite(), "entity", "zephyr_orb.png")
    import gen_mob_textures
    gen_mob_textures.generate(save)
    import gen_armor_models
    gen_armor_models.generate(save)
    import gen_weapon_models
    gen_weapon_models.generate(save)
    preview()
    print(f"{len(WRITTEN)} textures written to {os.path.normpath(ROOT)}; preview: {PREVIEW}")


if __name__ == "__main__":
    main()
