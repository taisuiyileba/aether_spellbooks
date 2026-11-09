"""Paint the three worn accessory atlases with colors from their shipped item icons.

The box sizes and UV offsets match AccessoryModels.java. Each model unit uses one
texel. Run with --check to compare the generated pixels with the game assets.
"""

import argparse
from pathlib import Path

from PIL import Image, ImageColor


TEXTURES = (Path(__file__).resolve().parents[1]
            / "src/main/resources/assets/aether_spellbooks/textures/models/accessory")
PENDANT = {
    "d": "#301c06", "g": "#5e3c0c", "m": "#c08620", "G": "#eeb846", "Y": "#fff2a8",
    "v": "#1c0634", "s": "#4e1e94", "p": "#7436d2", "P": "#a068f4", "L": "#d2b2ff", "W": "#ffffff",
}
GLOVES = {
    "d": "#2c0a2c", "s": "#5e1a58", "m": "#942c8c", "h": "#c64cba", "H": "#ea84dc", "L": "#ffc8f6",
    "g": "#c89024", "G": "#f0c048", "Y": "#fff0a0", "c": "#1e8ec4", "C": "#7ae8ff", "W": "#ffffff",
}
MANTLE = {
    "D": "#5e3e08", "g": "#8a6414", "G": "#e3b53a", "y": "#f8d878", "Y": "#fff3c4",
    "c": "#6fc8f0", "C": "#d8f4ff", "o": "#48486a", "s": "#9c9ebe", "m": "#cdd0e4",
    "w": "#eef0f8", "W": "#ffffff",
}


def box(atlas, palette, uv, size, **faces):
    """Write a vanilla CubeListBuilder box net. Unpainted faces stay transparent."""
    u, v = uv
    w, h, d = size
    layout = {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }
    for face, paint in faces.items():
        x, y, width, height = layout[face]
        rows = (paint * width,) * height if isinstance(paint, str) else paint
        assert len(rows) == height and all(len(row) == width for row in rows), (face, rows, size)
        for dy, row in enumerate(rows):
            for dx, color in enumerate(row):
                if color != ".":
                    atlas.putpixel((x + dx, y + dy), ImageColor.getcolor(palette[color], "RGBA"))


def pendant():
    image = Image.new("RGBA", (32, 16))
    box(image, PENDANT, (0, 0), (8, 6, 4),
        top=("Y......m", "G......g", "m......G", "Y......m"),
        right=("..Gg", "...m", "....", "....", "....", "...."),
        front=("Y......m", ".G....G.", ".m....g.", "..Y..G..", "...Gm...", "........"),
        left=("gG..", "m...", "....", "....", "....", "...."),
        back=("gGmmmGYm", "........", "........", "........", "........", "........"))
    box(image, PENDANT, (24, 0), (1, 1, 1),
        top="Y", bottom="g", right="m", front="G", left="m", back="g")
    box(image, PENDANT, (24, 4), (2, 2, 1),
        top=("LP",), bottom="v", right=("P", "p"), front=("WL", "Ps"), left=("p", "s"), back="s")
    return image


def gloves():
    image = Image.new("RGBA", (64, 24))
    for u, width in ((0, 4), (32, 3)):
        # The slim arm has a narrower front and palm but the same depth and cuff height.
        def face(rows):
            return rows if width == 4 else tuple(row[:2] + row[3:] for row in rows)

        box(image, GLOVES, (u, 0), (width, 5, 4),
            top=face(("shhs", "mHhm", "mhhm", "ssss")),
            bottom=face(("ssss", "mHhs", "mhms", "ssss")),
            right=("sHhs", "mHhm", "hHCm", "smcs", "dssd"),
            front=face(("mHLm", "hHLm", "hHhs", "mhms", "dssd")),
            left=("shHs", "mhHm", "mCHh", "scms", "dssd"),
            back=face(("smms", "shhm", "mhms", "smms", "dssd")))
        box(image, GLOVES, (u, 10), (width, 1, 4),
            top="g", bottom="s", right=("gGYg",), left=("gYGg",),
            front=face(("gYGg",)), back=face(("gGGg",)))
        box(image, GLOVES, (u, 16), (2, 2, 1),
            top=("LC",), bottom="s", right=("C", "c"), front=("WC", "Cc"), left=("c", "s"), back="s")
    return image


def mantle():
    image = Image.new("RGBA", (40, 16))
    outside = (
        "..DggggD..",
        "..gyYYyg..",
        "..GYccGg..",
        ".oWwGGwso.",
        ".oWwwwwso.",
        ".oWwmwwso.",
        "oWWwmwwmso",
        "oWwwmwwmso",
        "oWwwmwGmso",
        "oWWwmGyGso",
        "oWwwmYyGso",
        "oWwwmGwmso",
        "oWwmmwwmso",
        "DgGyGYgGyD",
    )
    lining = (
        "..ggGGgg..",
        "..GwwwwG..",
        "..gGyyGg..",
        ".omwwmmso.",
        ".ommwmmso.",
        ".ommwmsso.",
        "ommmwmmsso",
        "ommswmmsso",
        "ommswmmsso",
        "ommwwmmsso",
        "ommswmmsso",
        "ommswmmsso",
        "ommswmmsso",
        "DgGGgGGGgD",
    )
    for uv, width, start, height, edge in (((0, 0), 6, 0, 3, "g"),
                                          ((16, 0), 8, 3, 3, "s"),
                                          ((0, 5), 10, 6, 8, "o")):
        inset = (10 - width) // 2
        outer_rows = tuple(row[inset:inset + width] for row in outside[start:start + height])
        inner_rows = tuple(row[inset:inset + width] for row in lining[start:start + height])
        box(image, MANTLE, uv, (width, height, 1), top=edge, bottom=edge,
            right=edge, left=edge, front=outer_rows, back=inner_rows)
    box(image, MANTLE, (24, 6), (1, 2, 1), top="g", bottom="D",
        right=("g", "D"), left=("g", "D"), front=("G", "D"), back=("g", "D"))
    box(image, MANTLE, (32, 6), (2, 2, 1), top="g", bottom="D",
        right=("G", "g"), left=("g", "D"), front=("Yy", "gD"), back=("yG", "Dg"))
    box(image, MANTLE, (24, 11), (2, 2, 1), top=("Yy",), bottom="G",
        right=("Y", "G"), left=("y", "g"), front=("Cc", "cc"), back="G")
    return image


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="verify the checked-in pixels")
    check = parser.parse_args().check
    for name, image in (("pendant/zanite_focus_pendant", pendant()),
                        ("gloves/gravitite_casting_gloves", gloves()),
                        ("capes/valkyrie_mantle", mantle())):
        path = TEXTURES / (name + ".png")
        if check:
            with Image.open(path) as current:
                assert current.size == image.size and current.convert("RGBA").tobytes() == image.tobytes(), path
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            image.save(path, optimize=True)
        print(f"{name}: {image.width}x{image.height}")


if __name__ == "__main__":
    main()
