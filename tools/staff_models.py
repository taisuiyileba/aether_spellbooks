"""Aether Spellbooks staffs: 3D item models and 64x64 face atlases (Forge 1.20.1).

What it makes (under src/main/resources/assets/aether_spellbooks/):
  models/item/<staff>.json       the 3D element model, shown as 3D in every view including the inventory
  textures/item/<staff>.png      the face atlas; animated, so frames are stacked: 64 x (64 * frames) (+ .mcmeta)
for zanite_staff, aercloud_staff, gravitite_staff, valkyrie_scepter and solar_scepter, and preview sheets in
build/art_preview/staffs/: staffs_icons.png (the retired 16x16 sketches, kept only as a design reference),
staffs_models.png (software renders of the 3D models), staffs_vs_iss.png (ours beside Iron's Spells staff models,
same renderer and scale), staffs_animation.png / .gif (every frame, and the real timing). The reference assets are
read from %TEMP%/asb_ref when present and are never copied.

Conventions
  * Every element size is a whole number of units (0 only for flat ornament planes) and every face maps exactly one
    texel per unit (the vanilla / Iron's density). Faces that are completely inside other elements are left out.
  * Rotations: one axis, +-22.5 or +-45 degrees (the only angles vanilla accepts). The script refuses coplanar faces
    of different elements that face the same way and overlap (z-fighting) and faces of zero area.
  * Planes carry one painted texture region; the back face reuses it mirrored so the outline matches. The valkyrie
    wings are not planes: wing_rows() builds them from solid feather rows so they have thickness from every side.
  * Light comes from the top left of each face: left column one step lighter, right column one step darker, metal caps
    lit on top. Ramps are hue-shifted (shadows cooler, highlights warmer). No noise, no dithering: wood is a few long
    grain streaks, metal a bright specular column, cloth slanted wrap turns, gems dark rim + bright facet + one glint.
  * Animation: frame 0 is the resting look and is shown most of the time; a glint or pulse runs every few seconds
    (the solar scepter flickers gently all the time). The icon silhouette never changes between frames.
  * Display transforms follow Iron's Spells' own staffs (models/item/ice_staff.json), GUI included, so the staffs sit
    in the inventory and in item frames the way the reference mod's do; the GUI keeps their rotation and gui_display()
    only shrinks/shifts each staff into the same slot footprint. The grip wrap is at model y 2..8.

Run (Pillow + numpy):
  python tools/staff_models.py           write models, textures and previews
  python tools/staff_models.py --check   rebuild in memory and verify the exported files are identical
All art is original; reference icons are only drawn next to ours in the preview.
"""
import argparse
import json
import math
import os
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/aether_spellbooks'
PREVIEW = ROOT / 'build/art_preview/staffs'
REF = Path(os.environ.get('TEMP', Path.home() / 'AppData/Local/Temp')) / 'asb_ref'   # optional, previews only
NS = 'aether_spellbooks'
TEX = 64


def hexc(h):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


# ---------------------------------------------------------------------------------------------------------- palettes
# index 0 = outline / deepest shadow ... last = highlight. Shadows drift cool, highlights warm.
RAMPS = {
    'skyroot': ['2a1f17', '4a3826', '6c573a', '8e784e', 'b19b68', 'd5c48e'],
    'ash': ['3a3846', '5c5966', '85807f', 'aca594', 'cfc8b1', 'ede7cf'],
    'gold': ['4c2209', '8a4812', 'c47c1c', 'e8aa34', 'fad664', 'fff5ba'],
    'silver': ['2c3045', '50586f', '7c849c', 'a8b0c4', 'd0d7e4', 'f4f7fb'],
    'iron': ['11111d', '1f2131', '313449', '484d64', '686e87', '939ab1'],
    'zanite': ['1c0a35', '3a1868', '602ea3', '8a50d4', 'b888f2', 'ead8ff'],
    'gravitite': ['380b33', '6a1860', 'a5318c', 'd657b6', 'f190d6', 'ffd8f5'],
    'cloud': ['55678b', '8395b7', 'adbcd7', 'd1dbed', 'ebf1fa', 'ffffff'],
    'sky': ['0e2a63', '1c4c9c', '2c78cc', '4fa4ee', '8dd0ff', 'e2f7ff'],
    'gem': ['0b1d58', '183a96', '2864d0', '4b93f0', '9acdff', 'f0faff'],
    'sun': ['681904', 'ae3807', 'e2680f', 'fb9a22', 'ffcc48', 'fff3b2'],
    'crimson': ['26060a', '4c0c12', '771619', 'a0231b', 'c83c1f', 'e86a34'],
    'violet_leather': ['180f21', '2b1b3a', '423057', '5b4574', '776092', '9a85b0'],
    'black_leather': ['0d0b12', '1b1721', '2a2533', '3c3446', '52485e', '6e6479'],
    'blue_cloth': ['121a42', '1f2d6c', '2e4496', '425eb4', '6683cc', '93acdf'],
    'feather': ['6a5a3a', 'a0864c', 'd4b45e', 'f2da8a', 'fff1c4', 'ffffff'],
}
RAMPS = {k: [hexc(c) for c in v] for k, v in RAMPS.items()}


def col(ramp, level):
    r = RAMPS[ramp]
    return r[max(0, min(len(r) - 1, int(level)))]


# ------------------------------------------------------------------------------------------------------- geometry
def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]], np.float64)


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]], np.float64)


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]], np.float64)


ROT = {'x': rot_x, 'y': rot_y, 'z': rot_z}
FACES = ('north', 'south', 'east', 'west', 'up', 'down')
ANGLES = (-45.0, -22.5, 22.5, 45.0)


class El:
    """A box (or a plane when one size is 0). paint(face, frame) -> RGBA array (h, w, 4) for each visible face.

    rot = (axis, angle, origin). plane_face picks which side of a plane carries the painted region."""

    def __init__(self, frm, to, paint, rot=None, plane_face=None, name=''):
        self.paint, self.rot, self.name = paint, rot, name
        size = [float(to[i]) - float(frm[i]) for i in range(3)]
        for s in size:
            if abs(s - round(s)) > 1e-6 or s < -1e-9:
                raise ValueError(f'{name}: element sizes must be whole units, got {size}')
        self.size = [int(round(s)) for s in size]
        self.frm = [round(float(v), 4) for v in frm]
        self.to = [self.frm[i] + self.size[i] for i in range(3)]
        zero = [i for i in range(3) if self.size[i] == 0]
        if len(zero) > 1:
            raise ValueError(f'{name}: degenerate element')
        if zero:
            pair = {0: ('east', 'west'), 1: ('up', 'down'), 2: ('north', 'south')}[zero[0]]
            self.faces = list(pair)
            self.plane = plane_face or pair[1 if zero[0] == 2 else 0]
            if self.plane not in pair:
                raise ValueError(f'{name}: bad plane face')
        else:
            self.faces = list(FACES)
            self.plane = None
        self.matrix = np.eye(3)
        self.origin = np.zeros(3)
        if rot:
            axis, angle, origin = rot
            origin = [round(float(v), 4) for v in origin]
            self.rot = (axis, angle, origin)
            if angle not in ANGLES:
                raise ValueError(f'{name}: rotation {angle} not allowed')
            self.matrix = ROT[axis](math.radians(angle))
            self.origin = np.array(origin, np.float64)
        self.uv = {}

    def face_size(self, face):
        dx, dy, dz = self.size
        return {'north': (dx, dy), 'south': (dx, dy), 'east': (dz, dy), 'west': (dz, dy), 'up': (dx, dz), 'down': (dx, dz)}[face]

    def quad(self, face):
        """(p0, ex, ey) in model units before rotation: texel (u, v) sits at p0 + u/w*ex + v/h*ey."""
        (x1, y1, z1), (x2, y2, z2) = self.frm, self.to
        dx, dy, dz = x2 - x1, y2 - y1, z2 - z1
        return {
            'north': ((x2, y2, z1), (-dx, 0, 0), (0, -dy, 0)),
            'south': ((x1, y2, z2), (dx, 0, 0), (0, -dy, 0)),
            'east': ((x2, y2, z2), (0, 0, -dz), (0, -dy, 0)),
            'west': ((x1, y2, z1), (0, 0, dz), (0, -dy, 0)),
            'up': ((x1, y2, z1), (dx, 0, 0), (0, 0, dz)),
            'down': ((x1, y1, z2), (dx, 0, 0), (0, 0, -dz)),
        }[face]

    def world(self, p):
        return self.matrix @ (np.asarray(p, np.float64) - self.origin) + self.origin

    def world_quad(self, face):
        p0, ex, ey = self.quad(face)
        if self.plane and face != self.plane:            # mirrored back of a plane starts at the other edge
            p0, ex = np.add(p0, ex), np.negative(ex)
        return self.world(p0), self.matrix @ np.asarray(ex, np.float64), self.matrix @ np.asarray(ey, np.float64)

    def normal(self, face):
        n = {'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (1, 0, 0), 'west': (-1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}[face]
        return self.matrix @ np.array(n, np.float64)

    def inside(self, p, eps=1e-4):
        q = self.matrix.T @ (np.asarray(p, np.float64) - self.origin) + self.origin
        return all(self.frm[i] - eps < q[i] < self.to[i] + eps for i in range(3)) and self.plane is None


class Face:
    """What a painter sees: size, face name, world position of every texel centre and the element."""

    def __init__(self, el, face):
        self.el, self.name = el, face
        self.w, self.h = el.face_size(face)
        p0, ex, ey = el.quad(face)
        u = (np.arange(self.w) + 0.5) / max(1, self.w)
        v = (np.arange(self.h) + 0.5) / max(1, self.h)
        uu, vv = np.meshgrid(u, v)
        local = np.asarray(p0) + uu[..., None] * np.asarray(ex) + vv[..., None] * np.asarray(ey)
        self.pos = (local - el.origin) @ el.matrix.T + el.origin        # (h, w, 3) world positions


# ------------------------------------------------------------------------------------------------------- painters
SHIFT = {'up': 1, 'down': -1, 'north': 0, 'south': 0, 'east': 0, 'west': 0}


def levels_to_rgba(ramp, lv):
    r = np.array(RAMPS[ramp], np.uint8)
    return r[np.clip(np.rint(lv).astype(int), 0, len(r) - 1)]


def bevel(f, lv, top=1, bottom=-1, left=1, right=-1):
    """Light from the top left: brighter first column / row, darker last ones (only where the face is big enough)."""
    h, w = lv.shape
    if f.name in ('up', 'down'):
        top, bottom = 0, 0
    if w >= 2:
        lv[:, 0] += left
        lv[:, -1] += right
    if h >= 3:
        lv[0, :] += top
        lv[-1, :] += bottom
    return lv


def metal(ramp, base=3, spec=True):
    """Polished metal: bright specular column on wide faces, lit top edge, dark bottom edge."""
    def paint(f, frame):
        lv = np.full((f.h, f.w), float(base + SHIFT[f.name]))
        bevel(f, lv)
        if spec and f.w >= 3 and f.name not in ('up', 'down'):
            lv[:, 1] += 1
        if f.name in ('up', 'down') and f.w >= 2 and f.h >= 2:
            lv[0, 0] += 1
            lv[-1, -1] -= 1
        return levels_to_rgba(ramp, lv)
    return paint


# long grain streaks: (start row, length, delta), repeated down a face with a per-face / per-column offset
GRAIN = ((2, 6, -1), (11, 4, 1), (17, 7, -1), (27, 5, 1), (34, 3, -1))


def wood(ramp, base=3, seed=0):
    """Wood: a few long grain streaks along the length, end grain with a lit rim."""
    def paint(f, frame):
        lv = np.full((f.h, f.w), float(base + SHIFT[f.name]))
        if f.name in ('up', 'down'):
            if f.w >= 2 and f.h >= 2:
                lv[0, :] += 1
                lv[:, 0] += 1
                lv[-1, :] -= 1
            return levels_to_rgba(ramp, lv)
        k = FACES.index(f.name) * 9 + seed * 5
        for c in range(f.w):
            for start, length, d in GRAIN:
                s = (start + k + c * 13) % 40
                for r in range(s, min(f.h, s + length)):
                    lv[r, c] += d
        if f.w >= 2:
            lv[:, 0] += 1
            lv[:, -1] -= 1
        return levels_to_rgba(ramp, lv)
    return paint


def cloth(ramp, bind=None, base=2):
    """Grip wrap: three-row turns (lit edge, body, shadowed seam) that step down one row at each corner, so the
    strap spirals round the grip; low contrast like cloth. Metal binding on the end rows."""
    def paint(f, frame):
        lv = np.full((f.h, f.w), float(base + SHIFT[f.name]))
        if f.name not in ('up', 'down'):
            off = {'north': 0, 'east': 1, 'south': 2, 'west': 0}[f.name]
            for y in range(f.h):
                lv[y, :] += (1, 0, -1)[(y + off) % 3]
            if f.w >= 2:
                lv[:, -1] -= 0.5
        out = levels_to_rgba(ramp, lv)
        if bind and f.name not in ('up', 'down') and f.h >= 4:
            out[0, :] = col(bind, 4)
            out[-1, :] = col(bind, 2)
            if f.w >= 2:
                out[0, -1] = col(bind, 3)
                out[-1, 0] = col(bind, 3)
        return out
    return paint


def glow_bump(fx, frame, pos):
    """fx: ('sweep', y0, y1, n): a bright band climbs from y0 to y1 during frames 1..n;
           ('table', [bump per frame]): the whole part brightens by that many ramp steps."""
    z = np.zeros(pos.shape[:-1])
    if fx is None or frame == 0:
        return z
    if fx[0] == 'sweep':
        y0, y1, n = fx[1:4]
        yc = y0 + (y1 - y0) * (frame - 1) / max(1, n - 1)
        d = np.abs(pos[..., 1] - yc)
        return np.where(d < 0.8, 2.0, np.where(d < 1.8, 1.0, 0.0))
    if fx[0] == 'table':
        return z + fx[1][frame]
    raise ValueError(fx)


def apply_glow(lv, fx, frame, pos, top):
    """Add the glow; a table pulse stops at `cap` (fx[2], default one below the brightest step) so it stays
    saturated instead of washing out to white."""
    bump = glow_bump(fx, frame, pos)
    if fx is not None and fx[0] == 'table':
        cap = fx[2] if len(fx) > 2 else top - 1
        return np.where(bump > 0, np.minimum(lv + bump, np.maximum(lv, cap)), lv)
    return lv + bump


def gem(ramp, fx=None, base=3, glint=True):
    """Crystal face: lit left facet column, dark right rim, bright top edge, one white glint; tables lit."""
    def paint(f, frame):
        h, w = f.h, f.w
        lv = np.full((h, w), float(base))
        if f.name in ('up', 'down'):
            lv += 1 if f.name == 'up' else -1
            if w >= 2 and h >= 2:
                lv[-1, :] -= 1
                lv[:, -1] -= 1
                lv[0, 0] += 1
        else:
            if w >= 2:
                lv[:, 0] += 1
                lv[:, -1] -= 1
            if h >= 3:
                lv[0, :] += 1
                lv[-1, :] -= 1
        lv = apply_glow(lv, fx, frame, f.pos, len(RAMPS[ramp]) - 1)
        out = levels_to_rgba(ramp, lv)
        if glint and w >= 2 and h >= 3 and f.name in ('north', 'south', 'west'):
            out[1, 0] = RAMPS[ramp][-1]
        return out
    return paint


def cloud(ramp='cloud'):
    """Puff: sunlit top rim, soft body, cool shadowed underside; corners of the rim softened."""
    def paint(f, frame):
        h, w = f.h, f.w
        if f.name == 'up':
            lv = np.full((h, w), 5.0)
            if w >= 3 and h >= 3:
                lv[-1, :] = 4
                lv[:, -1] = 4
        elif f.name == 'down':
            lv = np.full((h, w), 1.0)
            if w >= 3 and h >= 3:
                lv[1:-1, 1:-1] = 2
        else:
            lv = np.full((h, w), 4.0)
            lv[0, :] = 5
            if h >= 3:
                lv[-1, :] = 2
            if h >= 4:
                lv[-2, :] = 3
            if w >= 3:
                lv[0, 0] = 4
                lv[0, -1] = 4
                lv[1:, -1] -= 1
        return levels_to_rgba(ramp, lv)
    return paint


def charmap(rows, pal, fx=None, glow=''):
    """Hand-placed pixel map for a face. pal: char -> (ramp, level); '.' transparent; glow chars animate."""
    def paint(f, frame):
        if len(rows) != f.h or any(len(r) != f.w for r in rows):
            raise ValueError(f'{f.el.name}: map is {len(rows[0])}x{len(rows)}, face is {f.w}x{f.h}')
        out = np.zeros((f.h, f.w, 4), np.uint8)
        bump = glow_bump(fx, frame, f.pos)
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in pal:
                    ramp, lv = pal[ch]
                    out[y, x] = col(ramp, lv + (bump[y, x] if ch in glow else 0))
        return out
    return paint


def rays(ramp, center, inner, outer, fx=None):
    """Sun ray: warm gold where it leaves the disc, orange, then a deep orange tip (by distance from centre)."""
    c = np.array(center, np.float64)

    def paint(f, frame):
        d = np.linalg.norm(f.pos - c, axis=-1)
        t = np.clip((d - inner) / max(1e-6, outer - inner), 0, 0.999)
        lv = 4.0 - np.floor(t * 3)
        if f.name in ('north', 'south') and f.w >= 1:
            lv[:, 0] += 0 if f.w == 1 else 1
        if f.name in ('down', 'east'):
            lv -= 1
        lv = np.where(t > 0.6, apply_glow(lv, fx, frame, f.pos, 5), lv)
        return levels_to_rgba(ramp, lv)
    return paint


def sun_disc(ramp, rim, center, fx=None):
    """Disc faces: concentric bands round the core on the front and back, a gold rim on the edges."""
    c = np.array(center, np.float64)

    def paint(f, frame):
        if f.name in ('north', 'south'):
            d = np.linalg.norm((f.pos - c)[..., :2], axis=-1)
            lv = np.where(d < 1.6, 5.0, np.where(d < 2.6, 4.0, np.where(d < 3.4, 3.0, 2.0)))
            lv = np.where(d < 2.6, apply_glow(lv, fx, frame, f.pos, 5), lv)
            return levels_to_rgba(ramp, lv)
        lv = np.full((f.h, f.w), 3.0 + SHIFT[f.name])
        bevel(f, lv)
        return levels_to_rgba(rim, lv)
    return paint


def spiral(base, ramp, level, period=8, step=2, thick=1, y0=-16.0, y1=32.0, every=1):
    """Overlay a one-texel line that winds round a shaft: on each side face it sits `step` rows lower than on the
    previous one (north, east, south, west), repeating every `period` rows; `thick` rows tall. every=2 keeps every
    other turn (rune marks)."""
    order = {'north': 0, 'east': 1, 'south': 2, 'west': 3}

    def paint(f, frame):
        out = base(f, frame).copy()
        if f.name not in order:
            return out
        for r in range(f.h):
            y = f.pos[r, 0, 1]
            if not y0 <= y < y1:
                continue
            k = int(math.floor(y)) + order[f.name] * step
            if k % period < thick and (k // period) % every == 0:
                out[r, :] = col(ramp, level)
        return out
    return paint


# --------------------------------------------------------------------------------------------- shared staff parts
def rot(axis, angle, origin):
    return (axis, float(angle), [float(v) for v in origin])


def lower_staff(shaft, band, grip, cap, tip, top=16):
    """Pommel tip + cap, shaft, a band below the hand, the grip wrap at the hand (y 2..8), a band above, collar."""
    return [
        El((7.5, -16, 7.5), (8.5, -15, 8.5), tip, name='pommel_tip'),
        El((7, -15, 7), (9, -13, 9), cap, name='pommel_cap'),
        El((7.5, -13, 7.5), (8.5, top, 8.5), shaft, name='shaft'),
        El((7, -9, 7), (9, -8, 9), band, name='band_low'),
        El((7, 2, 7), (9, 8, 9), grip, name='grip'),
        El((7, 12, 7), (9, 13, 9), band, name='band_high'),
        El((7, 15, 7), (9, 17, 9), cap, name='collar'),
    ]


def chain(axis, depth, start, segs, paint, name):
    """A jointed 1x1 prong in the XY plane (axis 'z', depth = z of its centre) or the ZY plane (axis 'x').

    start = (a, y): a is x (or z) of the first segment's base centre. segs = [(length, lean)], lean in degrees
    towards +a. Each segment starts 0.5*sin(bend) past the hinge so neighbouring segments never overlap coplanar."""
    out, prev = [], None
    a, y = start
    for i, (length, lean) in enumerate(segs):
        th = math.radians(lean)
        if prev is not None:
            shift = math.ceil(0.5 * abs(math.sin(th - prev)) * 1e4) / 1e4
            a, y = a + shift * math.sin(th), y + shift * math.cos(th)
        if axis == 'z':
            frm, to = (a - 0.5, y, depth - 0.5), (a + 0.5, y + length, depth + 0.5)
            r = rot('z', -lean, (a, y, depth)) if lean else None
        else:
            frm, to = (depth - 0.5, y, a - 0.5), (depth + 0.5, y + length, a + 0.5)
            r = rot('x', lean, (depth, y, a)) if lean else None
        out.append(El(frm, to, paint, r, name=f'{name}_{i}'))
        a, y = a + length * math.sin(th), y + length * math.cos(th)
        prev = th
    return out


def octagon(axis, center, apothem, length, paint, name, lift=0.25):
    """A ring of eight 1x1 bars round `axis` through `center`: four straight bars and four turned 45 degrees.
    The turned bars are lifted by `lift` along the axis so the joints never share a face plane."""
    cx, cy, cz = center
    h = length / 2
    out = []
    if axis == 'y':
        bars = [((cx + apothem - 0.5, cy - 0.5, cz - h), (cx + apothem + 0.5, cy + 0.5, cz + h)),
                ((cx - apothem - 0.5, cy - 0.5, cz - h), (cx - apothem + 0.5, cy + 0.5, cz + h))]
        cross = [((cx - h, cy - 0.5, cz + apothem - 0.5), (cx + h, cy + 0.5, cz + apothem + 0.5)),
                 ((cx - h, cy - 0.5, cz - apothem - 0.5), (cx + h, cy + 0.5, cz - apothem + 0.5))]
        off = (0, lift, 0)
    else:
        bars = [((cx - h, cy + apothem - 0.5, cz - 0.5), (cx + h, cy + apothem + 0.5, cz + 0.5)),
                ((cx - h, cy - apothem - 0.5, cz - 0.5), (cx + h, cy - apothem + 0.5, cz + 0.5))]
        cross = [((cx + apothem - 0.5, cy - h, cz - 0.5), (cx + apothem + 0.5, cy + h, cz + 0.5)),
                 ((cx - apothem - 0.5, cy - h, cz - 0.5), (cx - apothem + 0.5, cy + h, cz + 0.5))]
        off = (0, 0, lift)
    for i, (f, t) in enumerate(bars + cross):
        out.append(El(f, t, paint, name=f'{name}_{i}'))
    for i, (f, t) in enumerate(bars):
        for s in (-45, 45):
            f2, t2 = np.add(f, off), np.add(t, off)
            out.append(El(f2, t2, paint, rot(axis, s, center), name=f'{name}_d{i}{"ab"[s > 0]}'))
    return out


def puff(center, size, paint, name):
    """A cloud puff: two crossed boxes, one a unit longer in x, the other a unit longer in z and a unit taller."""
    (cx, cy, cz), (sx, sy, sz) = center, size
    return [El((cx - sx / 2, cy - sy / 2, cz - (sz - 1) / 2), (cx + sx / 2, cy + sy / 2, cz + (sz - 1) / 2), paint,
               name=name + '_a'),
            El((cx - (sx - 1) / 2, cy - (sy + 1) / 2, cz - sz / 2), (cx + (sx - 1) / 2, cy + (sy + 1) / 2, cz + sz / 2),
               paint, name=name + '_b')]


def y45(origin):
    return rot('y', 45, origin)


def feather(length, along, tip_high):
    """Painter for one modelled feather: a bar `length` units long along `along` ('x' or 'y'), its tip at the high end
    of that axis when `tip_high`. No painted outline - the bar's own edges and the face shading draw it. Colour runs
    from warm gold at the quill to white at the tip; the lit side of the vane (top / left) is one step brighter."""
    def level(d):                                      # d = texels from the tip
        if d < 2:
            return 'feather', 5 - d
        return 'gold', 4 if d < length * 0.6 else 3

    def paint(f, frame):
        out = np.zeros((f.h, f.w, 4), np.uint8)
        for v in range(f.h):
            for u in range(f.w):
                if along == 'y':
                    if f.name == 'up':
                        d = 0 if tip_high else length - 1
                    elif f.name == 'down':
                        d = length - 1 if tip_high else 0
                    else:
                        d = v if tip_high else length - 1 - v
                    shade = -1 if (f.name in ('south', 'north') and u > 0) or f.name in ('east', 'west', 'down') else 0
                else:
                    if f.name in ('east', 'west'):
                        d = 0 if (f.name == 'east') == tip_high else length - 1
                    elif f.name == 'north':
                        d = u if tip_high else length - 1 - u
                    else:
                        d = length - 1 - u if tip_high else u
                    shade = -1 if (f.name in ('south', 'north') and v > 0) or f.name in ('down', 'east', 'west') else 0
                ramp, lv = level(d)
                out[v, u] = col(ramp, lv + shade)
        return out
    return paint


def covert(f, frame):
    """The wing's shoulder: pale covert feathers on top, gold below, no outline."""
    out = np.zeros((f.h, f.w, 4), np.uint8)
    for v in range(f.h):
        for u in range(f.w):
            if f.name == 'up':
                ramp, lv = 'feather', 5 if u % 2 == 0 else 4
            elif f.name == 'down':
                ramp, lv = 'gold', 2
            else:
                ramp, lv = ('feather', 4) if v == 0 else ('gold', 4) if v < f.h - 1 else ('gold', 3)
                if f.name in ('east', 'west', 'north'):
                    lv -= 1
            out[v, u] = col(ramp, lv)
    return out


def raised_wing(side):
    """One modelled wing beside the scepter's gem: a covert shoulder and four feathers fanned upwards and outwards at
    0, 22.5, 45 and 67.5 degrees above the horizontal, the middle two longest. `side` is -1 for the left wing (outer
    side towards -x), +1 for the right. Every feather sits on its own depth so no two faces share a plane."""
    s = side
    mx = (lambda x: x) if s < 0 else (lambda x: 16 - x)       # mirror x for the right wing
    root = (mx(5.5), 21.5, 8)
    els = []
    # shoulder block over the feather roots (in front of them all)
    x0, x1 = sorted((mx(3.5), mx(7.5)))
    els.append(El((x0, 20.5, 6.75), (x1, 23.5, 9.75), covert, name=f'wing_{"lr"[s > 0]}_shoulder'))
    # horizontal feather (0 deg) and one tilted 22.5 deg up: bars along x, tip at the outer end. Feathers are three
    # units wide so neighbours overlap (each on its own depth) and the fan reads as one wing, not separate sticks.
    for i, (length, ang, z) in enumerate(((5, 0, 8.5), (7, 22.5, 8.0))):
        a, b = sorted((mx(5.5 - length), mx(5.5)))
        bar = El((a, 20, z - 0.5), (b, 23, z + 0.5), feather(length, 'x', tip_high=s > 0),
                 rot('z', -ang * -s, root) if ang else None, name=f'wing_{"lr"[s > 0]}_f{i}')
        els.append(bar)
    # 45 and 67.5 deg: vertical bars tilted outwards by 45 / 22.5 deg
    for i, (length, ang, z) in enumerate(((9, 45, 7.5), (8, 22.5, 8.75)), start=2):
        bar = El((root[0] - 1.5, 21.5, z - 0.5), (root[0] + 1.5, 21.5 + length, z + 0.5), feather(length, 'y', tip_high=True),
                 rot('z', ang * -s, root), name=f'wing_{"lr"[s > 0]}_f{i}')
        els.append(bar)
    return els


# ------------------------------------------------------------------------------------------------- the five staffs
def zanite_staff():
    """Skyroot shaft with gold fittings; two curled skyroot claws grip a zanite crystal cluster."""
    n = 6
    g = gem('zanite', ('sweep', 17.5, 27.5, n))
    els = lower_staff(wood('skyroot', 3), metal('gold', 3), cloth('violet_leather', 'gold'), metal('gold', 3),
                      gem('zanite', glint=False))
    els.append(El((6.5, 17, 6.5), (9.5, 18, 9.5), wood('skyroot', 2, seed=3), name='cup'))
    claw = wood('skyroot', 3, seed=1)
    for s in (-1, 1):
        els += chain('z', 8, (8 + 1.5 * s, 17.5), [(2, 45 * s), (3, 0), (2, -45 * s)], claw, 'claw' + 'we'[s > 0])
    els += [
        El((6.5, 18, 6.5), (9.5, 24, 9.5), g, y45((8, 21, 8)), name='crystal'),
        El((7, 24, 7), (9, 26, 9), g, y45((8, 21, 8)), name='crystal_tip'),
        El((7.5, 26, 7.5), (8.5, 27, 8.5), g, y45((8, 21, 8)), name='crystal_apex'),
        El((7, 18, 8.5), (9, 22, 10.5), g, rot('x', 22.5, (8, 18, 9.5)), name='shard_s'),
        El((7.5, 22, 9), (8.5, 23, 10), g, rot('x', 22.5, (8, 18, 9.5)), name='shard_s_tip'),
        El((7, 18, 5.5), (9, 21, 7.5), g, rot('x', -22.5, (8, 18, 6.5)), name='shard_n'),
        El((7.5, 21, 6), (8.5, 22, 7), g, rot('x', -22.5, (8, 18, 6.5)), name='shard_n_tip'),
    ]
    return els, 1 + n


def aercloud_staff():
    """Pale ash shaft with silver fittings; cloud puffs wrapped round a pulsing sky-blue core."""
    fx = ('table', [0, 1, 2, 3])
    c = cloud()
    els = lower_staff(wood('ash', 3), metal('silver', 3), cloth('blue_cloth', 'silver'), metal('silver', 3),
                      gem('sky', glint=False))
    els.append(El((6.5, 19, 6.5), (9.5, 22, 9.5), gem('sky', fx), y45((8, 20.5, 8)), name='core'))
    els += puff((8, 18, 8), (5, 2, 5), c, 'puff_low')
    els += puff((4.5, 20, 8), (3, 3, 4), c, 'puff_w')
    els += puff((11.5, 21, 7.5), (3, 3, 3), c, 'puff_e')
    els += puff((8.5, 23.5, 7), (4, 2, 3), c, 'puff_top')
    els += [
        El((2, 20, 8.5), (3, 21, 10.5), c, name='wisp_w'),
        El((12.5, 22.5, 7), (13.5, 23.5, 8), c, name='wisp_e'),
        El((6, 24.5, 6.5), (7, 25.5, 7.5), c, name='wisp_top'),
    ]
    return els, 4


def gravitite_staff():
    """Dark iron shaft with gravitite bands; a gravitite shard floats inside two orbiting silver rings."""
    fx = ('table', [0, 1, 2, 3])
    g = gem('gravitite', fx)
    iron = metal('iron', 3)
    shaft = spiral(iron, 'gravitite', 3, period=6, step=3, thick=2, every=2)
    els = lower_staff(shaft, metal('gravitite', 3), cloth('black_leather', 'iron'), iron, gem('gravitite', glint=False))
    els.append(El((6.5, 17, 6.5), (9.5, 18, 9.5), iron, name='cup'))
    els += [
        El((6.5, 21.5, 6.5), (9.5, 25.5, 9.5), g, y45((8, 23.5, 8)), name='shard'),
        El((7, 25.5, 7), (9, 26.5, 9), g, y45((8, 23.5, 8)), name='shard_top'),
        El((7.5, 26.5, 7.5), (8.5, 27.5, 8.5), g, y45((8, 23.5, 8)), name='shard_apex'),
        El((7, 20.5, 7), (9, 21.5, 9), g, y45((8, 23.5, 8)), name='shard_bottom'),
        El((7.5, 19.5, 7.5), (8.5, 20.5, 8.5), g, y45((8, 23.5, 8)), name='shard_point'),
    ]
    ring = metal('silver', 3, spec=False)
    els += octagon('y', (8, 23.5, 8), 3.5, 3, ring, 'ring_h')
    els += octagon('z', (8, 23.5, 8), 4.5, 4, ring, 'ring_v')
    return els, 4


def valkyrie_scepter():
    """White-silver shaft with gold bands; golden wings swept back from a faceted valkyrie-blue gem."""
    n = 5
    g = gem('gem', ('sweep', 18.0, 23.0, n))
    gold = metal('gold', 3)
    shaft = spiral(metal('silver', 4), 'gold', 3, period=8, step=2, thick=2)
    els = lower_staff(shaft, gold, cloth('blue_cloth', 'gold'), gold, gem('gem', glint=False))
    els += [
        El((6.5, 17, 6.5), (9.5, 18, 9.5), gold, name='neck'),
        El((6.5, 18, 6.5), (9.5, 21, 9.5), g, y45((8, 19.5, 8)), name='gem'),
        El((7, 21, 7), (9, 22, 9), g, y45((8, 19.5, 8)), name='gem_top'),
        El((7.5, 22, 7.5), (8.5, 24, 8.5), gold, name='finial'),
    ]
    # Modelled wings: a covert shoulder and four fanned feather bars on each side, rising up and out from the gem.
    els += raised_wing(-1) + raised_wing(1)
    return els, 1 + n


def solar_scepter():
    """Gold shaft with a crimson grip; a domed sun disc with sixteen rays round a flickering ember core."""
    fx = ('table', [0, 1, 2, 1], 5)
    cx, cy = 8, 22
    gold = metal('gold', 3)
    disc = sun_disc('sun', 'gold', (cx, cy, 8), fx)
    shaft = spiral(gold, 'gold', 2, period=4, step=1)
    els = lower_staff(shaft, metal('crimson', 3), cloth('crimson', 'gold'), gold, gem('sun', glint=False))
    els.append(El((6.5, 17, 6.5), (9.5, 18, 9.5), gold, name='neck'))
    for i, (y0, y1, half) in enumerate(((18, 19, 2), (19, 20, 3), (20, 24, 4), (24, 25, 3), (25, 26, 2))):
        els.append(El((cx - half, y0, 7), (cx + half, y1, 9), disc, name=f'disc_{i}'))
    els += [
        El((6.5, 20.5, 6), (9.5, 23.5, 10), disc, name='boss'),
        El((7.5, 21.5, 5.5), (8.5, 22.5, 10.5), gem('sun', fx, base=4, glint=False), name='core'),
    ]
    ray = rays('sun', (cx, cy, 8), 4.0, 7.0, ('table', [0, 1, 0, 1], 5))
    base_dirs = {'up': 0, 'right': 90, 'down': 180, 'left': 270}
    for k in range(16):
        theta = k * 22.5                                  # clockwise from straight up
        if theta == 180:
            continue                                      # the neck is there
        long_ray = k % 2 == 0
        length = 4 if long_ray else 3
        # pick the bar direction nearest to theta that is reachable with a rotation of -45..45 (prefer exact)
        for bname, bdeg in base_dirs.items():
            diff = (theta - bdeg + 540) % 360 - 180
            if -22.5 <= diff <= 45 and not (diff == -45):
                break
        r0, r1 = 3, 3 + length
        if bname == 'up':
            frm, to = (cx - 0.5, cy + r0, 7.5), (cx + 0.5, cy + r1, 8.5)
        elif bname == 'down':
            frm, to = (cx - 0.5, cy - r1, 7.5), (cx + 0.5, cy - r0, 8.5)
        elif bname == 'right':
            frm, to = (cx + r0, cy - 0.5, 7.5), (cx + r1, cy + 0.5, 8.5)
        else:
            frm, to = (cx - r1, cy - 0.5, 7.5), (cx - r0, cy + 0.5, 8.5)
        # clockwise in the picture (seen from the south, +x right) is a negative rotation about z
        r = rot('z', -diff, (cx, cy, 8)) if diff else None
        els.append(El(frm, to, ray, r, name=f'ray_{k}'))
    return els, 4


STAFFS = {
    'zanite_staff': zanite_staff,
    'aercloud_staff': aercloud_staff,
    'gravitite_staff': gravitite_staff,
    'valkyrie_scepter': valkyrie_scepter,
    'solar_scepter': solar_scepter,
}

DISPLAY = {
    'thirdperson_righthand': {'translation': [0, 3, 1.25]},
    'thirdperson_lefthand': {'translation': [0, 3, 1.25]},
    'firstperson_righthand': {'translation': [1.5, 0, -2.75]},
    'firstperson_lefthand': {'translation': [1.5, 0, -2.75]},
    'ground': {'translation': [0, 9.5, 0], 'scale': [0.5, 0.5, 0.5]},
    # GUI: replaced per staff by gui_display(), which keeps this rotation (Iron's Spells' own staffs, e.g.
    # assets/irons_spellbooks/models/item/ice_staff.json) and only fits scale and translation to the slot
    'gui': {'rotation': [-13, 128, 65], 'translation': [0.75, 0.5, 0], 'scale': [0.75, 0.75, 0.75]},
    'fixed': {'translation': [0, 1.75, -0.75], 'scale': [0.55, 0.55, 0.55]},
    'head': {'translation': [0, 14, -8]},
}
# The screen box (GUI pixels around the slot centre) that Iron's Spells' ice, blood, graybeard staffs and artificer cane
# fill with the transform above, averaged. Ours are longer (up to y 29 instead of 24) and the valkyrie wings and sun
# disc are wide, so the same scale pushes the heads past the slot; gui_display() fits each staff into this box.
ISS_GUI_BOX = ((-8.8, -9.3), (9.1, 8.9))


def gui_display(els):
    """The GUI transform for one staff: the reference rotation, then the largest scale (at most the reference 0.75) and
    the translation that put the projected model inside ISS_GUI_BOX with its centre on the box centre."""
    rx, ry, rz = (math.radians(a) for a in DISPLAY['gui']['rotation'])
    r = rot_x(rx) @ rot_y(ry) @ rot_z(rz)          # JOML rotationXYZ, as ItemTransform applies it
    pts = []
    for el in els:
        (x1, y1, z1), (x2, y2, z2) = el.frm, el.to
        for c in ((x, y, z) for x in (x1, x2) for y in (y1, y2) for z in (z1, z2)):
            pts.append(el.world(c))
    q = (np.array(pts) - 8.0) @ r.T
    lo, hi = q[:, :2].min(0), q[:, :2].max(0)
    (bx0, by0), (bx1, by1) = ISS_GUI_BOX
    k = min(0.75, (bx1 - bx0) / (hi[0] - lo[0]), (by1 - by0) / (hi[1] - lo[1]))
    t = (np.array([bx0 + bx1, by0 + by1]) - k * (lo + hi)) / 2
    k, tx, ty = round(k, 3), round(float(t[0]), 2), round(float(t[1]), 2)
    return dict(DISPLAY['gui'], translation=[tx, ty, 0], scale=[k, k, k])


# ------------------------------------------------------------------------------------------------ validation
def culled(el, face, els):
    """True when the whole face is buried inside other (solid) elements."""
    if el.plane:
        return False
    p0, ex, ey = el.world_quad(face)
    n = el.normal(face)
    w, h = el.face_size(face)
    for i in range(2 * w):
        for j in range(2 * h):
            p = p0 + (i + 0.5) / (2 * w) * ex + (j + 0.5) / (2 * h) * ey + n * 0.01
            if not any(o is not el and o.inside(p) for o in els):
                return False
    return True


def clip_poly(poly, a, b):
    """Sutherland-Hodgman: keep the part of poly left of the directed edge a->b (2D)."""
    out = []
    side = lambda p: (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0])
    for i in range(len(poly)):
        p, q = poly[i], poly[(i + 1) % len(poly)]
        sp, sq = side(p), side(q)
        if sp >= 0:
            out.append(p)
        if (sp >= 0) != (sq >= 0):
            t = sp / (sp - sq)
            out.append((p[0] + t * (q[0] - p[0]), p[1] + t * (q[1] - p[1])))
    return out


def poly_area(poly):
    return 0.5 * abs(sum(poly[i][0] * poly[(i + 1) % len(poly)][1] - poly[(i + 1) % len(poly)][0] * poly[i][1]
                         for i in range(len(poly))))


def check_model(name, els):
    problems = []
    if len(els) > 40:
        problems.append(f'{len(els)} elements (> 40)')
    vis = []
    for el in els:
        for v in el.frm + el.to:
            if not -16 <= v <= 32:
                problems.append(f'{el.name}: coordinate {v} outside -16..32')
        for face in el.faces:
            w, h = el.face_size(face)
            if w * h == 0:
                problems.append(f'{el.name}.{face}: zero area')
                continue
            if face in el.visible:
                vis.append((el, face))
    for i in range(len(vis)):
        ea, fa = vis[i]
        pa, xa, ya = ea.world_quad(fa)
        na = ea.normal(fa)
        for j in range(i + 1, len(vis)):
            eb, fb = vis[j]
            if eb is ea:
                continue
            nb = eb.normal(fb)
            if np.dot(na, nb) < 0.9999:
                continue
            pb, xb, yb = eb.world_quad(fb)
            if abs(np.dot(na, pb - pa)) > 1e-4:
                continue
            # project both quads onto the plane basis of face a
            u = xa / np.linalg.norm(xa)
            v = np.cross(na, u)
            to2 = lambda p: (float(np.dot(p - pa, u)), float(np.dot(p - pa, v)))
            qa = [to2(pa), to2(pa + xa), to2(pa + xa + ya), to2(pa + ya)]
            qb = [to2(pb), to2(pb + xb), to2(pb + xb + yb), to2(pb + yb)]
            if poly_area_signed(qa) < 0:
                qa = qa[::-1]
            if poly_area_signed(qb) < 0:
                qb = qb[::-1]
            inter = qb
            for k in range(4):
                inter = clip_poly(inter, qa[k], qa[(k + 1) % 4])
                if not inter:
                    break
            if inter and poly_area(inter) > 1e-3:
                problems.append(f'z-fighting: {ea.name}.{fa} / {eb.name}.{fb} ({poly_area(inter):.2f})')
    return problems


def poly_area_signed(poly):
    return 0.5 * sum(poly[i][0] * poly[(i + 1) % len(poly)][1] - poly[(i + 1) % len(poly)][0] * poly[i][1]
                     for i in range(len(poly)))


# ------------------------------------------------------------------------------------------ atlas and model export
def build_staff(name, fn):
    els, frames = fn()
    for el in els:
        el.visible = [f for f in el.faces if (el.plane is None or f == el.plane) and not culled(el, f, els)]
        el.drawn = [f for f in el.faces if f in el.visible or (el.plane and f != el.plane)]
    problems = check_model(name, els)
    # paint every visible face for every frame, de-duplicate identical faces, then shelf-pack
    painted, rects, order = {}, {}, []
    for el in els:
        for face in el.visible:
            f = Face(el, face)
            stack = np.stack([el.paint(f, k) for k in range(frames)])
            if stack.shape[1:3] != (f.h, f.w):
                raise ValueError(f'{name}/{el.name}.{face}: painter returned {stack.shape}')
            key = stack.tobytes() + bytes(stack.shape)
            if key not in painted:
                painted[key] = stack
                order.append(key)
            rects[(id(el), face)] = key
    atlas = np.zeros((frames, TEX, TEX, 4), np.uint8)
    place = {}
    x = y = row_h = 0
    for key in sorted(order, key=lambda k: (-painted[k].shape[1], -painted[k].shape[2], order.index(k))):
        _, h, w, _ = painted[key].shape
        if x + w > TEX:
            x, y, row_h = 0, y + row_h, 0
        if y + h > TEX:
            raise ValueError(f'{name}: atlas overflow')
        atlas[:, y:y + h, x:x + w] = painted[key]
        place[key] = (x, y, w, h)
        x += w
        row_h = max(row_h, h)
    s = 16 / TEX
    out = []
    for el in els:
        faces = {}
        for face in el.drawn:
            src = el.plane if el.plane else face
            u, v, w, h = place[rects[(id(el), src)]]
            uv = [u * s, v * s, (u + w) * s, (v + h) * s]
            if el.plane and face != el.plane:
                uv = [uv[2], uv[1], uv[0], uv[3]]
            faces[face] = {'uv': [num(c) for c in uv], 'texture': '#0'}
            el.uv[face] = (u, v, w, h, el.plane is not None and face != el.plane)
        entry = {'name': el.name, 'from': [num(c) for c in el.frm], 'to': [num(c) for c in el.to]}
        if el.rot:
            entry['rotation'] = {'angle': el.rot[1], 'axis': el.rot[0], 'origin': [num(c) for c in el.rot[2]]}
        entry['faces'] = faces
        out.append(entry)
    tex = f'{NS}:item/{name}'
    display = dict(DISPLAY, gui=gui_display(els))
    model3d = {'credit': 'Generated by tools/staff_models.py', 'texture_size': [TEX, TEX], 'gui_light': 'front',
               'textures': {'0': tex, 'particle': tex}, 'elements': out, 'display': display}
    strip = Image.fromarray(atlas.reshape(frames * TEX, TEX, 4), 'RGBA')
    return {'els': els, 'model3d': model3d, 'atlas': atlas, 'strip': strip, 'frames': frames, 'problems': problems}


def num(v):
    v = round(float(v), 4)
    return int(v) if v == int(v) else v


def dump_model(model):
    """Readable JSON: one element per line."""
    if 'elements' not in model:
        return json.dumps(model, indent=2) + '\n'
    head = {k: v for k, v in model.items() if k != 'elements' and k != 'display'}
    lines = ['{']
    for k, v in head.items():
        lines.append(f'  {json.dumps(k)}: {json.dumps(v)},')
    lines.append('  "elements": [')
    lines.append(',\n'.join('    ' + json.dumps(e) for e in model['elements']))
    lines.append('  ],')
    lines.append('  "display": {')
    lines.append(',\n'.join(f'    {json.dumps(k)}: {json.dumps(v)}' for k, v in model['display'].items()))
    lines.append('  }')
    lines.append('}')
    return '\n'.join(lines) + '\n'


# ------------------------------------------------------------------------------------------------- GUI icons
# 16 rows of 16 chars per icon, '.' transparent. Every icon names five ramps and uses the same letters for them:
#   shaft  '0'..'5' = levels 0..5          fit  'k' 0, 'y' 1, 'f' 2, 'g' 3, 'G' 4, 'H' 5   (bands, collar, pommel)
#   head   'Z' 0, 'z' 1, 'm' 2, 'M' 3, 'P' 4, '*' 5  (the glowing part)
#   sec    'q' 0, 'r' 1, 's' 2, 't' 3, 'u' 4, 'v' 5  (claws, clouds, rings, wings, rays)
#   grip   'n' 0, 'l' 1, 'L' 3
# anim: [(chars, fx)], fx = ('sweep', frames): a two-pixel glint runs up the staff axis over those pixels;
#                      fx = ('table', [bump per frame]): those pixels brighten by that many ramp steps.
ICON_LETTERS = {
    'shaft': {str(i): i for i in range(6)},
    'fit': {'k': 0, 'y': 1, 'f': 2, 'g': 3, 'G': 4, 'H': 5},
    'head': {'Z': 0, 'z': 1, 'm': 2, 'M': 3, 'P': 4, '*': 5},
    'sec': {'q': 0, 'r': 1, 's': 2, 't': 3, 'u': 4, 'v': 5},
    'grip': {'n': 0, 'l': 1, 'L': 3},
}

ICONS = {
    'zanite_staff': {
        'ramps': {'shaft': 'skyroot', 'fit': 'gold', 'head': 'zanite', 'sec': 'skyroot', 'grip': 'violet_leather'},
        'anim': [('zmMP*', ('sweep', 6))],
        'rows': [
            "..............zZ",
            ".............zPZ",
            "........q...zPMZ",
            ".......qtq.zPMmZ",
            ".......qtqzP*MzZ",
            ".......qtzPPMmZq",
            ".......qZPMMmzZq",
            "......kGZMmmzZtq",
            ".....kGgyZZZZtq.",
            "......4ykqttqq..",
            ".....430........",
            "....430.........",
            "...430..........",
            ".k430...........",
            "kGg0............",
            "kyk.............",
        ],
    },
    'aercloud_staff': {
        'ramps': {'shaft': 'ash', 'fit': 'silver', 'head': 'sky', 'sec': 'cloud', 'grip': 'blue_cloth'},
        'anim': [('zmMP*', ('table', [0, 1, 2, 3]))],
        'rows': [
            "................",
            "..........qqq...",
            "......qqqqvvuq..",
            ".....qvvuqvuuuq.",
            ".....quuqZZZqtq.",
            "......qZP*MZqq..",
            ".....qZPMMmzZuq.",
            "....qvuZMmzZvuuq",
            "....quuqZzZqutq.",
            ".....kGqqZqqqq..",
            ".....4yk........",
            "....430.........",
            "...430..........",
            ".k430...........",
            "kGg0............",
            "kyk.............",
        ],
    },
    'gravitite_staff': {
        'ramps': {'shaft': 'iron', 'fit': 'silver', 'head': 'gravitite', 'sec': 'silver', 'grip': 'black_leather'},
        'anim': [('zmMP*', ('table', [0, 1, 2, 3]))],
        'rows': [
            "..........uuuqZ.",
            ".........u...zPZ",
            "........u...zPMZ",
            "........u..zP*mt",
            "........t.zPMmZs",
            "........tzMmzZ.s",
            ".......kzMzZ..s.",
            "......kGzZssss..",
            ".....kGyk.......",
            "......k.........",
            ".....430........",
            "....430.........",
            "...430..........",
            ".k430...........",
            "kGg0............",
            "kyk.............",
        ],
    },
    'valkyrie_scepter': {
        'ramps': {'shaft': 'silver', 'fit': 'gold', 'head': 'gem', 'sec': 'gold', 'grip': 'blue_cloth'},
        'anim': [('zmMP*', ('sweep', 5))],
        'rows': [
            ".......qq.......",
            "......qvuq......",
            ".....qvutq......",
            "....qvutsq.Z....",
            ".....qutsqZPZ...",
            "....qvtssZP*mZ..",
            ".....qqtsrZmZ...",
            ".......qkGkZqqqq",
            ".......kGgkssstu",
            ".......Gyktsttuv",
            ".....430..qtuuvq",
            "....430...qvqvq.",
            "...430.....q.q..",
            ".k430...........",
            "kGg0............",
            "kyk.............",
        ],
    },
    'solar_scepter': {
        'ramps': {'shaft': 'gold', 'fit': 'gold', 'head': 'sun', 'sec': 'sun', 'grip': 'crimson'},
        'anim': [('mMP*', ('table', [0, 1, 2, 1], 5)), ('s', ('table', [0, 1, 0, 1], 5))],
        'rows': [
            "...........s....",
            "........s..s..s.",
            ".........sZZZs..",
            ".......ssZPMmZss",
            "........ZP**MZ..",
            "......ssZM*MmZss",
            ".........ZmmZ...",
            ".......s.kZZks.s",
            "........kGgk....",
            ".......kGgk.s...",
            "......4nlk......",
            ".....430........",
            "....430.........",
            "...430..........",
            ".k430...........",
            "kGg0............",
        ],
    },
}


def icon_frames(name, frames):
    spec = ICONS[name]
    rows = spec['rows']
    if len(rows) != 16 or any(len(r) != 16 for r in rows):
        raise ValueError(f'{name}: icon must be 16x16')
    lookup = {}
    for part, letters in ICON_LETTERS.items():
        for ch, lv in letters.items():
            lookup[ch] = (spec['ramps'][part], lv)
    out = []
    for k in range(frames):
        lv = np.full((16, 16), -1.0)
        ramp = [[None] * 16 for _ in range(16)]
        for y in range(16):
            for x in range(16):
                ch = rows[y][x]
                if ch == '.':
                    continue
                if ch not in lookup:
                    raise ValueError(f'{name}: unknown icon char {ch!r}')
                ramp[y][x], lv[y, x] = lookup[ch]
        if k:
            for chars, fx in spec['anim']:
                pts = [(x, y) for y in range(16) for x in range(16) if rows[y][x] in chars]
                if fx[0] == 'sweep':
                    us = [x - y for x, y in pts]
                    uc = min(us) + (max(us) - min(us)) * (k - 1) / max(1, fx[1] - 1)
                    for x, y in pts:
                        d = abs((x - y) - uc)
                        lv[y, x] += 2 if d < 0.75 else (1 if d < 1.75 else 0)
                else:
                    cap = fx[2] if len(fx) > 2 else 4
                    for x, y in pts:
                        if fx[1][k]:
                            lv[y, x] = min(lv[y, x] + fx[1][k], max(lv[y, x], cap))
        im = np.zeros((16, 16, 4), np.uint8)
        for y in range(16):
            for x in range(16):
                if ramp[y][x]:
                    im[y, x] = col(ramp[y][x], lv[y, x])
        out.append(im)
    return np.stack(out)


# ------------------------------------------------------------------------------------------------- animation
# (frametime, frame sequence). Frame 0 rests; the effect runs once per cycle. GUI icon and 3D atlas share it.
def rest_then(n_rest, effect, frametime=2):
    return frametime, [0] * n_rest + list(effect)


SEQ = {
    'zanite_staff': rest_then(36, [1, 2, 3, 4, 5, 6]),                     # 4.2 s cycle, 0.6 s glint up the crystal
    'aercloud_staff': rest_then(40, [1, 2, 3, 3, 2, 1]),                   # 4.6 s cycle, the core swells and fades
    'gravitite_staff': rest_then(30, [1, 2, 3, 2, 1, 2, 3, 2, 1]),         # 3.9 s cycle, a double throb
    'valkyrie_scepter': rest_then(44, [1, 2, 3, 4, 5]),                    # 4.9 s cycle, glint across the gem
    'solar_scepter': (3, [0, 1, 2, 1, 0, 3, 2, 3, 1, 0, 2, 1]),           # continuous gentle flicker, 1.8 s loop
}


def mcmeta(name):
    ft, frames = SEQ[name]
    return json.dumps({'animation': {'frametime': ft, 'interpolate': False, 'frames': frames}}) + '\n'


# ------------------------------------------------------------------------------------------------- preview renderer
class Raster:
    """Orthographic textured-quad rasteriser: z-buffer, back-face culling, nearest texel, alpha test."""

    def __init__(self, w, h, scale, yaw, pitch, center, bg=(0.23, 0.24, 0.29)):
        self.w, self.h, self.scale = w, h, scale
        self.view = rot_x(math.radians(pitch)) @ rot_y(math.radians(yaw))
        self.center = np.array(center, np.float64)
        self.rgb = np.zeros((h, w, 3), np.float32)
        self.rgb[:] = bg
        self.z = np.full((h, w), np.inf)

    def project(self, p):
        v = self.view @ (np.asarray(p, np.float64) - self.center)
        return np.array([self.w / 2 - v[0] * self.scale, self.h / 2 - v[1] * self.scale]), v[2]

    def quad(self, tex, rect, p0, ex, ey, normal):
        nv = self.view @ normal
        if nv[2] >= -1e-6:
            return
        light = shade(normal)
        a, za = self.project(p0)
        b, zb = self.project(p0 + ex)
        c, zc = self.project(p0 + ey)
        e1, e2 = b - a, c - a
        det = e1[0] * e2[1] - e1[1] * e2[0]
        if abs(det) < 1e-9:
            return
        corners = np.array([a, b, c, b + e2])
        lo = np.clip(np.floor(corners.min(0)).astype(int), 0, [self.w, self.h])
        hi = np.clip(np.ceil(corners.max(0)).astype(int), 0, [self.w, self.h])
        if lo[0] >= hi[0] or lo[1] >= hi[1]:
            return
        gy, gx = np.mgrid[lo[1]:hi[1], lo[0]:hi[0]].astype(np.float64) + 0.5
        dx, dy = gx - a[0], gy - a[1]
        s = (dx * e2[1] - dy * e2[0]) / det
        t = (e1[0] * dy - e1[1] * dx) / det
        inside = (s >= 0) & (s < 1) & (t >= 0) & (t < 1)
        u1, v1, u2, v2 = rect
        tx = np.clip(np.floor(u1 + s * (u2 - u1)).astype(int), int(min(u1, u2)), int(math.ceil(max(u1, u2))) - 1)
        ty = np.clip(np.floor(v1 + t * (v2 - v1)).astype(int), int(min(v1, v2)), int(math.ceil(max(v1, v2))) - 1)
        texel = tex[ty, tx]
        z = za + s * (zb - za) + t * (zc - za)
        zbuf = self.z[lo[1]:hi[1], lo[0]:hi[0]]
        m = inside & (texel[..., 3] > 0.5) & (z < zbuf)
        zbuf[m] = z[m]
        self.rgb[lo[1]:hi[1], lo[0]:hi[0]][m] = texel[m, :3] * light

    def image(self):
        return Image.fromarray((np.clip(self.rgb, 0, 1) * 255).astype(np.uint8), 'RGB')


def shade(n):
    """Minecraft-like directional shading: up 1.0, down 0.5, north/south 0.8, east/west 0.6."""
    n = n / np.linalg.norm(n)
    up = 1.0 if n[1] > 0 else 0.5
    return float(n[0] ** 2 * 0.6 + n[1] ** 2 * up + n[2] ** 2 * 0.8)


def render(staff, frame, w, h, scale, yaw, pitch, center):
    r = Raster(w, h, scale, yaw, pitch, center)
    tex = staff['atlas'][frame].astype(np.float32) / 255.0
    for el in staff['els']:
        for face in el.drawn:
            u, v, fw, fh, _ = el.uv[face]
            p0, ex, ey = el.world_quad(face)
            r.quad(tex, (u, v, u + fw, v + fh), p0, ex, ey, el.normal(face))
    return r.image()


def load_ref_model(rel):
    """Preview only: a reference (Blockbench JSON) item model as (els, textures) for render_ref."""
    path = REF / rel
    try:
        model = json.loads(path.read_text(encoding='utf-8'))
    except OSError:
        return None
    root = path.parents[2]
    texs = {}
    for key, ref in model.get('textures', {}).items():
        ns, name = ref.split(':') if ':' in ref else ('minecraft', ref)
        img = Image.open(root.parent / ns / 'textures' / (name + '.png')).convert('RGBA')
        texs['#' + key] = np.asarray(img.crop((0, 0, img.width, img.width)), np.float32) / 255.0
    quads = []
    for e in model['elements']:
        dummy = El.__new__(El)
        dummy.frm, dummy.to = e['from'], e['to']
        r = e.get('rotation')
        dummy.matrix, dummy.origin, dummy.plane = np.eye(3), np.zeros(3), None
        if r and r.get('angle'):
            dummy.matrix = ROT[r['axis']](math.radians(r['angle']))
            dummy.origin = np.array(r['origin'], np.float64)
        for face, fd in e['faces'].items():
            tex = texs.get(fd.get('texture', '#0'))
            if tex is None or 'uv' not in fd:
                continue
            k = tex.shape[1] / 16
            uv = [c * k for c in fd['uv']]
            if uv[0] == uv[2] or uv[1] == uv[3]:
                continue
            p0, ex, ey = El.quad(dummy, face)
            n = El.normal(dummy, face)
            quads.append((tex, uv, El.world(dummy, p0), dummy.matrix @ np.asarray(ex, np.float64),
                          dummy.matrix @ np.asarray(ey, np.float64), n))
    return quads


def render_ref(quads, w, h, scale, yaw, pitch, center):
    r = Raster(w, h, scale, yaw, pitch, center)
    for tex, uv, p0, ex, ey, n in quads:
        r.quad(tex, uv, p0, ex, ey, n)
    return r.image()


REF_MODELS = ['graybeard_staff', 'ice_staff', 'blood_staff', 'artificer_cane', 'lightning_rod']


# ------------------------------------------------------------------------------------------------- previews
def ref_icon(rel):
    try:
        return Image.open(REF / rel).convert('RGBA')
    except OSError:
        return None


REF_ICONS = [
    ('ISS hither_thither_wand', 'iss/assets/irons_spellbooks/textures/item/hither_thither_wand.png'),
    ('ISS blood_staff_gui', 'iss/assets/irons_spellbooks/textures/item/blood_staff_gui.png'),
    ('ISS firebrand_gui', 'iss/assets/irons_spellbooks/textures/item/firebrand_gui.png'),
    ('Aether nature_staff', 'aether/assets/aether/textures/item/miscellaneous/nature_staff.png'),
    ('Aether cloud_staff', 'aether/assets/aether/textures/item/miscellaneous/cloud_staff.png'),
]


def slot_bg(img16, scale):
    """An inventory-slot-like square behind the icon, upscaled with nearest neighbour."""
    bg = Image.new('RGBA', (18, 18), (139, 139, 139, 255))
    d = ImageDraw.Draw(bg)
    d.line([(0, 0), (17, 0)], fill=(55, 55, 55, 255))
    d.line([(0, 0), (0, 17)], fill=(55, 55, 55, 255))
    d.line([(1, 17), (17, 17)], fill=(255, 255, 255, 255))
    d.line([(17, 1), (17, 17)], fill=(255, 255, 255, 255))
    bg.alpha_composite(img16, (1, 1))
    return bg.resize((18 * scale, 18 * scale), Image.NEAREST)


def on_grey(im, size):
    bg = Image.new('RGBA', (size, size), (139, 139, 139, 255))
    bg.alpha_composite(im.resize((size, size), Image.NEAREST))
    return bg.convert('RGB')


def previews(built, icons):
    PREVIEW.mkdir(parents=True, exist_ok=True)
    names = list(built)
    # 1) models: two full views + two head close-ups per staff
    cw, chh = 220, 300
    sheet = Image.new('RGB', (cw * 2 * len(names), chh + 250), (40, 42, 50))
    d = ImageDraw.Draw(sheet)
    for i, n in enumerate(names):
        st = built[n]
        for j, (yaw, pitch) in enumerate(((-35, -14), (130, -20))):
            sheet.paste(render(st, 0, cw, chh, 6.2, yaw, pitch, (8, 6, 8)), ((2 * i + j) * cw, 0))
        sheet.paste(render(st, 0, cw, 240, 13, -30, -18, (8, 21.5, 8)), (2 * i * cw, chh + 6))
        sheet.paste(render(st, 0, cw, 240, 13, 60, -10, (8, 21.5, 8)), ((2 * i + 1) * cw, chh + 6))
        d.text((2 * i * cw + 6, 4), f'{n} ({len(st["els"])} el.)', fill=(235, 235, 235))
    sheet.save(PREVIEW / 'staffs_models.png')

    # 2) icons: ours (frame 0) next to the references, 10x, slot 2x / 1x and bare 1x
    S = 10
    refs = [(lbl, ref_icon(p)) for lbl, p in REF_ICONS]
    items = [(n, Image.fromarray(icons[n][0], 'RGBA')) for n in names] + [(l, im) for l, im in refs if im is not None]
    W = len(items) * (16 * S + 12) + 12
    sheet = Image.new('RGBA', (W, 16 * S + 80), (139, 139, 139, 255))
    d = ImageDraw.Draw(sheet)
    for i, (lbl, im) in enumerate(items):
        x = 12 + i * (16 * S + 12)
        sheet.alpha_composite(im.resize((16 * S, 16 * S), Image.NEAREST), (x, 10))
        d.text((x, 16 * S + 14), lbl[:26], fill=(20, 20, 20))
        sheet.alpha_composite(slot_bg(im, 2), (x, 16 * S + 30))
        sheet.alpha_composite(slot_bg(im, 1), (x + 44, 16 * S + 30))
        sheet.alpha_composite(im, (x + 70, 16 * S + 32))
    sheet.save(PREVIEW / 'staffs_icons.png')

    # 3) animation strips: head close-up and icon for every frame
    rows = []
    for n in names:
        st = built[n]
        strip = Image.new('RGB', (st['frames'] * 130, 260), (40, 42, 50))
        for k in range(st['frames']):
            strip.paste(render(st, k, 130, 170, 9, -30, -15, (8, 21.5, 8)), (k * 130, 0))
            strip.paste(on_grey(Image.fromarray(icons[n][k], 'RGBA'), 80), (k * 130 + 25, 175))
        rows.append(strip)
    sheet = Image.new('RGB', (max(r.width for r in rows), sum(r.height for r in rows)), (40, 42, 50))
    y = 0
    for r in rows:
        sheet.paste(r, (0, y))
        y += r.height
    sheet.save(PREVIEW / 'staffs_animation.png')

    # 5) ours next to Iron's Spells staffs, same renderer, scale and angle
    refs = [(n, load_ref_model(f'iss/assets/irons_spellbooks/models/item/{n}.json')) for n in REF_MODELS]
    refs = [(n, q) for n, q in refs if q]
    if refs:
        cols = [(n, lambda yaw, pitch, sc, cy, h, st=built[n]: render(st, 0, 150, h, sc, yaw, pitch, (8, cy, 8)))
                for n in names]
        cols += [('ISS ' + n, lambda yaw, pitch, sc, cy, h, q=q: render_ref(q, 150, h, sc, yaw, pitch, (8, cy, 8)))
                 for n, q in refs]
        sheet = Image.new('RGB', (150 * len(cols), 300 + 220 + 16), (40, 42, 50))
        d = ImageDraw.Draw(sheet)
        for i, (lbl, fn) in enumerate(cols):
            sheet.paste(fn(-35, -14, 6.2, 6, 300), (150 * i, 16))
            sheet.paste(fn(-35, -14, 11, 20, 220), (150 * i, 316))
            d.text((150 * i + 4, 2), lbl[:24], fill=(235, 235, 235))
        sheet.save(PREVIEW / 'staffs_vs_iss.png')

    # 4) GIF with the real timing (one GIF frame per 2 game ticks)
    seqs = {n: [f for f in SEQ[n][1] for _ in range(SEQ[n][0])] for n in names}
    ticks = max(len(s) for s in seqs.values())
    heads = {n: [render(built[n], k, 120, 160, 8, -30, -15, (8, 21.5, 8)) for k in range(built[n]['frames'])]
             for n in names}
    gif = []
    for t in range(0, ticks, 2):
        fr = Image.new('RGB', (len(names) * 130, 270), (40, 42, 50))
        for i, n in enumerate(names):
            k = seqs[n][t % len(seqs[n])]
            fr.paste(heads[n][k], (i * 130 + 5, 0))
            fr.paste(on_grey(Image.fromarray(icons[n][k], 'RGBA'), 96), (i * 130 + 17, 166))
        gif.append(fr)
    gif[0].save(PREVIEW / 'staffs_animation.gif', save_all=True, append_images=gif[1:], duration=100, loop=0)


# ------------------------------------------------------------------------------------------------- main
def outputs():
    built, icons, files, problems = {}, {}, {}, []
    for name, fn in STAFFS.items():
        st = build_staff(name, fn)
        built[name] = st
        problems += [f'{name}: {p}' for p in st['problems']]
        ic = icon_frames(name, st['frames'])
        icons[name] = ic
        alpha = ic[..., 3] > 0
        if any(not np.array_equal(alpha[k], alpha[0]) for k in range(len(ic))):
            problems.append(f'{name}: icon silhouette changes between frames')
        if max(SEQ[name][1]) != st['frames'] - 1:
            problems.append(f'{name}: sequence and texture frame counts differ')
        files[f'models/item/{name}.json'] = dump_model(st['model3d'])
        files[f'models/item/{name}_3d.json'] = None
        files[f'textures/item/{name}.png'] = st['strip']
        files[f'textures/item/{name}_gui.png'] = None
        anim = st['frames'] > 1
        files[f'textures/item/{name}.png.mcmeta'] = mcmeta(name) if anim else None
        files[f'textures/item/{name}_gui.png.mcmeta'] = None
    return built, icons, files, problems


def same(path, content):
    if content is None:
        return not path.exists()
    if not path.exists():
        return False
    if isinstance(content, Image.Image):
        old = Image.open(path)
        return old.mode == 'RGBA' and old.size == content.size and np.array_equal(np.asarray(old), np.asarray(content))
    return path.read_text(encoding='utf-8') == content


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--check', action='store_true', help='verify the exported files instead of writing them')
    ap.add_argument('--no-preview', action='store_true', help='skip the preview sheets')
    args = ap.parse_args()
    built, icons, files, problems = outputs()
    for p in problems:
        print('PROBLEM:', p)
    if args.check:
        bad = [rel for rel, c in files.items() if not same(ASSETS / rel, c)]
        for rel in bad:
            print('differs:', rel)
        print('check:', 'OK' if not bad and not problems else 'FAILED', f'({len(files)} files)')
        return 1 if bad or problems else 0
    for rel, c in files.items():
        path = ASSETS / rel
        if c is None:
            if path.exists():
                path.unlink()
            continue
        path.parent.mkdir(parents=True, exist_ok=True)
        if isinstance(c, Image.Image):
            c.save(path, optimize=True)
        else:
            path.write_text(c, encoding='utf-8', newline='\n')
    for n, st in built.items():
        print(f'{n}: {len(st["els"])} elements, {st["frames"]} frames')
    if not args.no_preview:
        previews(built, icons)
        print('previews in', PREVIEW)
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())
