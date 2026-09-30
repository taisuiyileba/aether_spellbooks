"""
Hand-built 16x16 item icons for Aether Spellbooks: the 3 boss spellbooks, the Zanite Focus Pendant
and the two mage armour sets.

Every pixel here is original. Only generic silhouettes/composition are shared with the games' icons
so ours sit naturally next to them in an inventory:
  * spellbooks - a closed book lying flat in the same isometric view as Iron's Spells' book icons
                 (long edge 2:1, short edge 1:1, 3px thick, pages showing on both front faces,
                 metal corner caps, cover emblem).
  * pendant    - the Aether accessory layout: a chain loop hanging towards a pendant bottom-right.
  * gloves     - the Aether gloves layout: a pair of rounded mitts overlapping on the diagonal, cuffs bottom-left.
  * template   - a vanilla smithing template: an upright stone tablet with an engraved motif.
  * armour     - vanilla armour icon silhouettes (helmet front-on, chestplate, leggings, boots
                 side-on with the toes pointing outwards like vanilla), two-tone selective outline
                 (lighter top/left, darker bottom/right), light from the top-left.

Used by gen_textures.py (books, pendant, gloves) and gen_armor_models.py (armour icons, smithing template).
"""
from PIL import Image


def hex_to_rgba(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def render_icon(rows, pal):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), rows
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in pal:
                im.putpixel((x, y), hex_to_rgba(pal[ch]))
    return im


# ==============================================================================
# 1. SPELLBOOKS
# ==============================================================================
def book_template():
    """Region map {(x, y): kind} of the closed book.
    c cover face, e front-cover edge, P pages, q recessed page end, k back cover,
    o outline catching the light (back edges), O outline in shadow."""
    R = {}

    def bottom(x):  # bottom outline row of the right-hand (long) side face for column x
        return 14 - (x - 6) // 2

    for x in range(6, 16):
        for j, kind in enumerate("ePPkO"):
            R[(x, bottom(x) - 4 + j)] = kind
    for y in range(6, 15):
        for i, kind in enumerate("OkPPe"):
            x = y - 9 + i
            if 0 <= x <= 5 and ((x, y) not in R or kind == "O"):
                R[(x, y)] = kind
    spans = {1: (8, 10), 2: (6, 11), 3: (4, 12), 4: (2, 13), 5: (0, 14), 6: (0, 15), 7: (0, 15), 8: (0, 15), 9: (0, 15)}
    for y, (a, b) in spans.items():
        for x in range(a, b + 1):
            R.setdefault((x, y), "c")
    for y in range(1, 6):
        R[(10 - 2 * y, y)] = R[(11 - 2 * y, y)] = "o"
        R[(9 + y, y)] = "O"
    R[(8, 1)] = R[(9, 1)] = "o"
    R[(10, 1)] = R[(15, 6)] = "O"
    for y in (5, 6, 7, 8):
        R[(0, y)] = "o" if y < 7 else "O"
    # the page block sits slightly inside the covers at the right-hand corner
    del R[(15, 7)], R[(15, 8)]
    R[(14, 7)] = R[(14, 8)] = "q"
    return R


BAYER4 = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]


def render_book(spec):
    R = book_template()
    cov = [hex_to_rgba(c) for c in spec["cover"]]   # 5 shades, dark -> light
    pag = [hex_to_rgba(c) for c in spec["pages"]]   # 4 shades, dark -> light
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

    def near(x, y, kinds):
        return any(R.get((x + dx, y + dy)) in kinds for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))

    def lit_face(x, y):  # the short side face looks towards the light, the long one away from it
        return x <= 5 or (x == 6 and y >= 13)

    for (x, y), kind in R.items():
        if kind == "O":
            c = hex_to_rgba(spec["outline"])
        elif kind == "o":
            c = hex_to_rgba(spec["outline_light"])
        elif kind == "c":
            if near(x, y, "o"):                                  # light rim along the back edges
                c = cov[4]
            elif near(x, y, "e") or R.get((x, y + 1)) == "e":    # shade along the front edges
                c = cov[1]
            else:                                                # soft falloff + leather/stone grain
                v = 3.0 - (x * 0.35 + y * 0.9) / 11.0 * 1.6
                v += (BAYER4[y % 4][x % 4] / 16.0 - 0.5) * spec.get("grain", 0.6)
                c = cov[max(1, min(3, int(round(v))))]
        elif kind == "e":
            c = cov[2] if lit_face(x, y) else cov[1]
        elif kind == "k":
            c = cov[1] if lit_face(x, y) else cov[0]
        elif kind == "q":
            c = pag[0]
        else:  # pages: the sheet under the cover edge is brighter, every third column shows a gap
            if lit_face(x, y):
                c = pag[3] if R.get((x + 1, y)) == "e" else pag[2]
            else:
                upper = R.get((x, y - 1)) == "e"
                c = pag[2] if upper else (pag[0] if x % 3 == 0 else pag[1])
        im.putpixel((x, y), c)
    for rows, pal in spec["overlays"]:
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in pal and (x, y) in R:
                    im.putpixel((x, y), hex_to_rgba(pal[ch]))
    return im


GOLD_TRIM = {"1": "#7a4a10", "2": "#c88a22", "3": "#f0c048", "4": "#fff2a8"}
BRONZE_TRIM = {"1": "#5a2c10", "2": "#a4581c", "3": "#dc8a34", "4": "#ffd08a"}

# metal caps on the four corners of the front cover and the front corners of the back cover
BOOK_CORNERS = [
    "................",
    "........221.....",
    "........432.....",
    "................",
    "................",
    "232..........2..",
    "23...........321",
    "12..............",
    "1...............",
    ".2...343......21",
    ".....221........",
    "................",
    "................",
    ".....221........",
    "................",
    "................",
]

BOOK_SPECS = {
    # carved holystone slab from the Bronze Dungeon, bronze corners, the Slider's glowing eye
    "slider_codex": dict(
        cover=["#343c4e", "#525e76", "#74829c", "#96a4bc", "#c4cedf"],
        pages=["#7e7c70", "#aaa898", "#ccc8b6", "#e6e2d0"],
        outline="#151a24", outline_light="#2a3242", grain=1.2,
        overlays=[(BOOK_CORNERS, BRONZE_TRIM), ([
            "................",
            "................",
            "................",
            "................",
            ".....dqEEqd.....",
            "...dqEEWWEEqd...",
            ".....dqEEqd.....",
            "......llll......",
        ], {"d": "#262e40", "l": "#b4c0d4", "q": "#1c86ac", "E": "#58e6ff", "W": "#effeff"})],
    ),
    # royal-blue binding, gilded pages, a pair of white wings around a sky gem
    "valkyrie_grimoire": dict(
        cover=["#1a2a58", "#264484", "#365eae", "#4f7ed0", "#84acea"],
        pages=["#9a7630", "#d0ae58", "#ecd694", "#fcf0c8"],
        outline="#0c1430", outline_light="#1c2c5c",
        overlays=[(BOOK_CORNERS, GOLD_TRIM), ([
            "................",
            "................",
            "................",
            ".....W.....W....",
            ".....wW...Ww....",
            ".....vwW4Wwv....",
            "......vv3vv.....",
        ], {"W": "#ffffff", "w": "#d4e2f4", "v": "#8ea4cc", "4": "#c4f2ff", "3": "#3aa0ff"})],
    ),
    # crimson leather, gold corners, a blazing sun
    "solar_codex": dict(
        cover=["#3c0808", "#681010", "#961c14", "#c03420", "#e66a3a"],
        pages=["#96602a", "#cc9a52", "#eac886", "#faeabc"],
        outline="#1c0404", outline_light="#3c0a06",
        overlays=[(BOOK_CORNERS, GOLD_TRIM), ([
            "................",
            "................",
            "................",
            ".......y.y......",
            "......oYYYo.....",
            ".....yYWWWYy....",
            "......oYYYo.....",
            ".......y.y......",
        ], {"W": "#fffbe0", "Y": "#ffd23c", "y": "#f7a020", "o": "#e06410"})],
    ),
}


def slider_codex():
    return render_book(BOOK_SPECS["slider_codex"])


def valkyrie_grimoire():
    return render_book(BOOK_SPECS["valkyrie_grimoire"])


def solar_codex():
    return render_book(BOOK_SPECS["solar_codex"])


# ==============================================================================
# 2. ZANITE FOCUS PENDANT
# ==============================================================================
ZANITE_PENDANT_PAL = {
    "O": "#5e3c0c", "i": "#301c06",                                       # chain outline outside / inside the loop
    "o": "#4a2e08", "a": "#8a5a14", "b": "#c08620", "c": "#eeb846", "d": "#fff2a8",  # gold
    "k": "#1c0634", "1": "#2e0c52", "2": "#4e1e94", "3": "#7436d2", "4": "#a068f4", "5": "#d2b2ff", "W": "#ffffff",  # zanite
}
# faceted zanite in a gold cap; hangs where both ends of the chain meet
ZANITE_GEM = [
    "..ooo..",
    ".obdco.",
    "k45W43k",
    "k35432k",
    ".k4322k",
    "..k32k.",
    "...kk..",
]


def zanite_focus_pendant():
    right = [(4, 2), (5, 2), (6, 2), (7, 2), (8, 3), (9, 4), (9, 5), (9, 6), (9, 7), (9, 8)]
    left = [(3, 3), (2, 4), (2, 5), (2, 6), (2, 7), (2, 8), (3, 9), (4, 10), (5, 10), (6, 11), (7, 11)]
    M = {}
    for chain in (right, left):
        for i, (x, y) in enumerate(chain):  # links alternate; the top-left of the loop catches the light
            lit = x + y < 11
            M[(x, y)] = ("d" if lit else "c") if i % 2 == 0 else ("c" if lit else "b")
    gem = {(8 + i, 8 + j): ch for j, row in enumerate(ZANITE_GEM) for i, ch in enumerate(row) if ch != "."}
    solid = set(M) | set(gem)
    outside, stack = set(), [(0, 0)]
    while stack:
        p = stack.pop()
        if p in outside or p in solid or not (-1 <= p[0] <= 16 and -1 <= p[1] <= 16):
            continue
        outside.add(p)
        stack += [(p[0] + 1, p[1]), (p[0] - 1, p[1]), (p[0], p[1] + 1), (p[0], p[1] - 1)]
    for (x, y) in list(M):
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                q = (x + dx, y + dy)
                if q not in solid and q not in M:
                    M[q] = "O" if q in outside else "i"
    M.update(gem)
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for (x, y), ch in M.items():
        if 0 <= x < 16 and 0 <= y < 16:
            im.putpixel((x, y), hex_to_rgba(ZANITE_PENDANT_PAL[ch]))
    return im


# ==============================================================================
# 3. GRAVITITE CASTING GLOVES - gravitite mitts, gold-rimmed cuffs, a mana gem on the back of the hand
# ==============================================================================
GRAVITITE_GLOVES_PAL = {
    "0": "#2c0a2c", "1": "#5e1a58", "2": "#942c8c", "3": "#c64cba", "4": "#ea84dc", "5": "#ffc8f6",  # gravitite
    "b": "#8a5a14", "c": "#c89024", "d": "#f0c048", "e": "#fff0a0",                                  # gold rims
    "x": "#1e8ec4", "y": "#7ae8ff", "W": "#ffffff",                                                  # mana gem
}

GRAVITITE_GLOVES_ROWS = [
    "................",
    ".....000........",
    "....045400......",
    "...0454320......",
    "...0443000000...",
    "..043205554430..",
    "..032105544320..",
    ".0d11045443220..",
    ".01c104433Wy10..",
    "..01d03332yx10..",
    "...00d2221110...",
    "....02e11100....",
    ".....02de0......",
    "......0220......",
    ".......00.......",
    "................",
]


def gravitite_casting_gloves():
    return render_icon(GRAVITITE_GLOVES_ROWS, GRAVITITE_GLOVES_PAL)


# ==============================================================================
# 4. AETHER ARCANE UPGRADE SMITHING TEMPLATE - holystone tablet, engraved mana crystal between golden wings
# ==============================================================================
SMITHING_TEMPLATE_PAL = {
    "D": "#2e3038", "d": "#4a4e5a",                                                   # outline shaded / lit, engraving
    "1": "#6c707c", "2": "#8c909c", "3": "#a8acb6", "4": "#c4c8d0", "5": "#dfe2e8",   # holystone
    "f": "#1c8cb0", "g": "#46c4dc", "h": "#86ecf6", "W": "#ffffff",                   # mana crystal
    "a": "#6e4610", "b": "#b88020", "c": "#eab63e", "y": "#fff0a0",                   # ambrosium gold
}

SMITHING_TEMPLATE_ROWS = [
    "................",
    "....ddddddddd...",
    "...d544444433D..",
    "...d4434d4332D..",
    "...d433dhd323D..",
    "...dy3dghfd3cD..",
    "...dcydhWgdcbD..",
    "...dbcdghfdbaD..",
    "...d3bdfgfda2D..",
    "...d433dfd232D..",
    "...d3323d3222D..",
    "...d333232221D..",
    "...d232222121D..",
    "....D22212111D..",
    ".....DDDDDDDD...",
    "................",
]


def smithing_template():
    return render_icon(SMITHING_TEMPLATE_ROWS, SMITHING_TEMPLATE_PAL)


# ==============================================================================
# 5. VALKYRIE MAGE ARMOUR - silver plate, gold trim, blue robe, winged helm (matches the 3D model)
# ==============================================================================
VALK_ARMOR_PAL = {
    "K": "#2e364a", "k": "#161a26",                                                   # outline lit / shaded
    "1": "#5e6882", "2": "#8e98b2", "3": "#bec7d8", "4": "#dfe5f0", "W": "#ffffff",   # silver
    "o": "#4a300a", "a": "#8a5a12", "b": "#c48a1c", "c": "#ecb83e", "d": "#fff0a0",   # gold
    "x": "#1a2c62", "y": "#2c52a2", "z": "#4a80dc", "Z": "#8cc0fa",                   # blue robe
    "g": "#2a8ef0", "G": "#c8f4ff",                                                   # sky gem
    "i": "#0e1018", "j": "#1c202c",                                                   # helmet interior
}

VALK_HELMET_ROWS = [
    "................",
    ".o............o.",
    "odo..........odo",
    "ocdo.KKKKKK.odco",
    "obcdK3W4433kdcbo",
    ".oabK34W4332bao.",
    "..oKbcdGgccako..",
    "...K32jjjj21k...",
    "...K3jiiiii1k...",
    "...K3jiiiii1k...",
    "...Kbiiiiiiak...",
    "....Kk....kk....",
    "................",
    "................",
    "................",
    "................",
]

VALK_CHEST_ROWS = [
    "................",
    "................",
    ".KKKKK....KKKKK.",
    ".K4WcK....Kc43k.",
    ".K443cK..Kc332k.",
    ".K3334caac3321k.",
    ".Kbcc34W433cbak.",
    ".kk234cGgc321kk.",
    "...K4W3cb332k...",
    "...K443cb321k...",
    "...K343cb321k...",
    "...Kbccddcbak...",
    "...KzZzyyyxxk...",
    "....Kzyyyxk.....",
    ".....kkkkkk.....",
    "................",
]

VALK_LEGS_ROWS = [
    "................",
    "................",
    "....KKKKKKKk....",
    "...KcdcGgcbak...",
    "...KzZzyyyyxk...",
    "...KzZyyxyyxk...",
    "...Kzyykkyyxk...",
    "...Kzyk..Kyxk...",
    "...Kcbk..Kcak...",
    "...K4Wk..K43k...",
    "...K43k..K32k...",
    "...K32k..K21k...",
    "...Kcbk..Kbak...",
    "...kkkk..kkkk...",
    "................",
    "................",
]

# side-on like vanilla boots: left boot's toe points left, right boot's toe points right
VALK_BOOTS_ROWS = [
    "................",
    "................",
    "................",
    "....KKK..KKK....",
    "...Kdck..Kdck...",
    "...KW3k..KW3k...",
    "...K43k..K43k...",
    "...Kgzk..KGgk...",
    "...K42k..K32k...",
    "..K432k..K243k..",
    ".Kc432k..K234ck.",
    ".Kb21kk..kk12bk.",
    ".Kkkk......kkkk.",
    "................",
    "................",
    "................",
]

# ==============================================================================
# 6. PHOENIX MAGE ARMOUR - crimson robe, gold trim, flame crest and hems
# ==============================================================================
PHOENIX_ARMOR_PAL = {
    "K": "#4e140a", "k": "#240604",
    "1": "#5e1008", "2": "#8c1c0e", "3": "#b83018", "4": "#dc5224", "W": "#ff9658",   # crimson
    "o": "#4a2606", "a": "#8a5210", "b": "#c8861a", "c": "#f0b63a", "d": "#fff0a0",   # gold
    "f": "#b83208", "g": "#ee7216", "h": "#ffb62e", "H": "#fff4b0",                   # flame
    "r": "#ff5a1e", "R": "#ffd8a8",                                                   # ember gem
    "i": "#140604", "j": "#2c0c08",
}

PHOENIX_HELMET_ROWS = [
    ".......Hh.......",
    ".....h.hh.h.....",
    ".....hghhgh.....",
    ".....fggggf.....",
    "....K4W4332k....",
    "...K34W44321k...",
    "...KbcdRrcbak...",
    "...K32jjjj21k...",
    "...K3jhiihi1k...",
    "...K3jiiiii1k...",
    "...Kgiiiiiifk...",
    "....Kk....kk....",
    "................",
    "................",
    "................",
    "................",
]

PHOENIX_CHEST_ROWS = [
    "................",
    "................",
    ".KKKKK....KKKKK.",
    ".K4WcK....Kc43k.",
    ".K443cK..Kc332k.",
    ".K3334caac3321k.",
    ".Khgg34W433ggfk.",
    ".kk234cRrc321kk.",
    "...K4W3cb332k...",
    "...Kbccddcbak...",
    "...K3g333g21k...",
    "...Kghg3ghg1k...",
    "...KhHhghHhgk...",
    "....KghHhgfk....",
    ".....kkkkkk.....",
    "................",
]

PHOENIX_LEGS_ROWS = [
    "................",
    "................",
    "....KKKKKKKk....",
    "...KcdcRrcbak...",
    "...K4W433321k...",
    "...K4433c321k...",
    "...K432kk321k...",
    "...K43k..K21k...",
    "...K43k..K21k...",
    "...Kg3k..Kg1k...",
    "...Khgk..Khfk...",
    "...KHhk..Khgk...",
    "...Kcbk..Kbak...",
    "...kkkk..kkkk...",
    "................",
    "................",
]

PHOENIX_BOOTS_ROWS = [
    "................",
    "................",
    ".....h....h.....",
    "....hHh..hHh....",
    "...Kdck..Kdck...",
    "...K43k..K43k...",
    "...K43k..K32k...",
    "...K32k..K32k...",
    "...Kh2k..Kh2k...",
    "..K4g2k..K2g3k..",
    ".Kc432k..K234ck.",
    ".Kb21kk..kk12bk.",
    ".Kkkk......kkkk.",
    "................",
    "................",
    "................",
]


def valkyrie_mage_icons():
    return {
        "valkyrie_mage_helmet": render_icon(VALK_HELMET_ROWS, VALK_ARMOR_PAL),
        "valkyrie_mage_chestplate": render_icon(VALK_CHEST_ROWS, VALK_ARMOR_PAL),
        "valkyrie_mage_leggings": render_icon(VALK_LEGS_ROWS, VALK_ARMOR_PAL),
        "valkyrie_mage_boots": render_icon(VALK_BOOTS_ROWS, VALK_ARMOR_PAL),
    }


def phoenix_mage_icons():
    return {
        "phoenix_mage_helmet": render_icon(PHOENIX_HELMET_ROWS, PHOENIX_ARMOR_PAL),
        "phoenix_mage_chestplate": render_icon(PHOENIX_CHEST_ROWS, PHOENIX_ARMOR_PAL),
        "phoenix_mage_leggings": render_icon(PHOENIX_LEGS_ROWS, PHOENIX_ARMOR_PAL),
        "phoenix_mage_boots": render_icon(PHOENIX_BOOTS_ROWS, PHOENIX_ARMOR_PAL),
    }
