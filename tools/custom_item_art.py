"""
Pixel-crafted authentic Minecraft/Aether/Iron's Spells item textures for Aether Spellbooks.

Includes:
- 3 Boss Spellbooks (Slider Codex, Valkyrie Grimoire, Solar Codex) following Iron's Spells isometric template
- Zanite Focus Pendant following Aether accessory pendant style
- 2 Mage Armor Sets (Valkyrie Mage & Phoenix Mage, 4 pieces each) following vanilla/Aether armor icon style
"""
from PIL import Image

def hex_to_rgba(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)

def render_icon(rows, pal):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in pal:
                im.putpixel((x, y), pal[ch])
    return im

# ==============================================================================
# 1. SPELLBOOKS (Exact Iron's Spells isometric perspective & template)
# ==============================================================================

SLIDER_PAL = {
    "k": hex_to_rgba("#181a22"), # dark outline
    "K": hex_to_rgba("#262a34"), # mid outline
    "A": hex_to_rgba("#ffd684"), # bronze glint
    "B": hex_to_rgba("#ea9838"), # light bronze
    "C": hex_to_rgba("#b46420"), # mid bronze
    "D": hex_to_rgba("#7e3e10"), # dark bronze
    "1": hex_to_rgba("#9eaac0"), # holystone highlight
    "2": hex_to_rgba("#748096"), # mid holystone
    "3": hex_to_rgba("#525c70"), # shaded holystone
    "4": hex_to_rgba("#384050"), # deep stone shadow
    "W": hex_to_rgba("#ffffff"), # eye white pupil
    "E": hex_to_rgba("#6cf2ff"), # bright cyan flare
    "e": hex_to_rgba("#00c4e6"), # vibrant slider cyan
    "q": hex_to_rgba("#007898"), # deep cyan glow
    "Q": hex_to_rgba("#004054"), # eye socket outline
    "P": hex_to_rgba("#dce8f0"), # page highlight
    "p": hex_to_rgba("#a4b8c6"), # page midtone
    "r": hex_to_rgba("#728696"), # page shadow
    "R": hex_to_rgba("#4a5a68"), # page crevice
    "S": hex_to_rgba("#0e1014"), # shadow
}

SLIDER_ROWS = [
    "................",
    "........kkk.....",
    "......kkABCK....",
    "....kkABCD233K..",
    "..kkABCD222334K.",
    "kkABCD22QeeeQ3CK",
    "kABC222QeWWWeQBC",
    "kP12222QeWEEeQCP",
    "kPp12222QeeeQPpP",
    "kCPpCCCC22kPpPPk",
    ".kRppBCCCCkPpPkS",
    "..kRppppppkPkSS.",
    "...kRpppkSkS....",
    "....kBCkS.......",
    ".....kk.........",
    "................",
]

VALKYRIE_PAL = {
    "k": hex_to_rgba("#161826"),
    "K": hex_to_rgba("#262a3e"),
    "A": hex_to_rgba("#fff6be"), # gold glint
    "B": hex_to_rgba("#f6d052"), # bright gold
    "C": hex_to_rgba("#ce9e22"), # gold midtone
    "D": hex_to_rgba("#926810"), # gold shadow
    "1": hex_to_rgba("#7698dc"), # sky-cloth highlight
    "2": hex_to_rgba("#4e70b4"), # royal blue midtone
    "3": hex_to_rgba("#344c84"), # shaded blue cloth
    "4": hex_to_rgba("#203058"), # dark cloth
    "W": hex_to_rgba("#ffffff"), # wing highlight
    "w": hex_to_rgba("#dce6f4"), # soft wing white
    "v": hex_to_rgba("#9cb2cc"), # wing shadow
    "G": hex_to_rgba("#68c0ff"), # bright azure gem
    "g": hex_to_rgba("#1c72c8"), # deep sapphire
    "H": hex_to_rgba("#0a386e"), # gem socket
    "P": hex_to_rgba("#fff8d0"), # gilded page highlight
    "p": hex_to_rgba("#ebd06c"), # gilded page midtone
    "r": hex_to_rgba("#b89630"), # page shadow
    "R": hex_to_rgba("#785e1c"), # deep crevice
    "S": hex_to_rgba("#0c1018"),
}

VALKYRIE_ROWS = [
    "................",
    "........kkk.....",
    "......kkABCK....",
    "....kkABCD233K..",
    "..kkABCD222334K.",
    "kkABCD2Wwvv23BCK",
    "kABC22WwGWw22BCk",
    "kP12222wgw222BCP",
    "kPp12222H222PpPk",
    "kBPpBBBB22kPpPPk",
    ".kRppBBBBkkPpPkS",
    "..kRppppppkPkSS.",
    "...kRpppkSkS....",
    "....kBCkS.......",
    ".....kk.........",
    "................",
]

SOLAR_PAL = {
    "k": hex_to_rgba("#220606"),
    "K": hex_to_rgba("#380a08"),
    "A": hex_to_rgba("#ffea80"), # sun gold glint
    "B": hex_to_rgba("#f8b834"), # bright sun gold
    "C": hex_to_rgba("#dc8216"), # gold midtone
    "D": hex_to_rgba("#9c4c0a"), # gold shadow
    "1": hex_to_rgba("#cc2818"), # crimson highlight
    "2": hex_to_rgba("#981812"), # rich crimson
    "3": hex_to_rgba("#68100c"), # shaded leather
    "4": hex_to_rgba("#400806"), # dark charred leather
    "W": hex_to_rgba("#ffffff"), # sun core incandescent
    "Y": hex_to_rgba("#fff060"), # solar flare yellow
    "O": hex_to_rgba("#ff9816"), # flame orange
    "o": hex_to_rgba("#e24408"), # deep solar fire
    "q": hex_to_rgba("#981004"), # solar flare rim
    "P": hex_to_rgba("#fce890"), # amber page highlight
    "p": hex_to_rgba("#deb04a"), # amber page midtone
    "r": hex_to_rgba("#a47020"), # amber page shadow
    "R": hex_to_rgba("#684210"), # deep crevice
    "S": hex_to_rgba("#120404"),
}

SOLAR_ROWS = [
    "................",
    "........kkk.....",
    "......kkABCK....",
    "....kkABCD233K..",
    "..kkABCD2qOq34K.",
    "kkABCD22OYWO3BCk",
    "kABC222qYWWYOBCk",
    "kP122222OYWO2BCP",
    "kPp122222qOqPpPk",
    "kBPpBBBB22kPpPPk",
    ".kRppBBBBkkPpPkS",
    "..kRppppppkPkSS.",
    "...kRpppkSkS....",
    "....kBCkS.......",
    ".....kk.........",
    "................",
]

# ==============================================================================
# 2. ZANITE FOCUS PENDANT
# ==============================================================================
ZANITE_PENDANT_PAL = {
    "k": hex_to_rgba("#22083c"),
    "s": hex_to_rgba("#120424"),
    "A": hex_to_rgba("#ebe6f2"), # silver gleam
    "a": hex_to_rgba("#c09ff1"), # bright zanite link
    "b": hex_to_rgba("#9455f2"), # mid zanite link
    "G": hex_to_rgba("#f4d273"), # gold prongs highlight
    "d": hex_to_rgba("#8c5816"), # gold shadow
    "W": hex_to_rgba("#ffffff"), # specular glint
    "w": hex_to_rgba("#ebe6f2"), # light facet
    "1": hex_to_rgba("#c09ff1"), # bright violet facet
    "2": hex_to_rgba("#9455f2"), # vibrant royal violet
    "3": hex_to_rgba("#7a36e0"), # rich violet body
    "4": hex_to_rgba("#531fa0"), # deep violet
    "5": hex_to_rgba("#37136e"), # shadow facet
    "6": hex_to_rgba("#190838"), # deep base shadow
    "C": hex_to_rgba("#68f6ff"), # cyan mana core spark
}

ZANITE_PENDANT_ROWS = [
    "................",
    "................",
    "....kkkkk.......",
    "...kAaBbAk......",
    "..kAbssbsbAk....",
    "..kask..kbsk....",
    "..kbsk....kask..",
    "..kask....kbsk..",
    "..kbsk...kask...",
    "...kask.kbGdk...",
    "....kbkkgWw1Gk..",
    ".....kgW11234dk.",
    "......kW1C2345k.",
    "......kd23456k..",
    ".......kk66kk...",
    "................",
]

# ==============================================================================
# 3. VALKYRIE MAGE ARMOR
# ==============================================================================
VALK_ARMOR_PAL = {
    "k": hex_to_rgba("#1e2230"),
    "S": hex_to_rgba("#f6f8fd"), # specular silver
    "s": hex_to_rgba("#d8deea"), # bright silver
    "1": hex_to_rgba("#aeb7ca"), # mid silver
    "2": hex_to_rgba("#7e88a0"), # dark silver
    "3": hex_to_rgba("#4e566c"), # deep shadow silver
    "G": hex_to_rgba("#fff1c2"), # gold glint
    "g": hex_to_rgba("#f4d273"), # bright gold
    "4": hex_to_rgba("#d8a63a"), # gold midtone
    "5": hex_to_rgba("#9c6a1c"), # gold shadow
    "6": hex_to_rgba("#5e3c0c"), # deep gold rim
    "B": hex_to_rgba("#a4d0f8"), # sky blue highlight
    "b": hex_to_rgba("#6caae8"), # sky blue midtone
    "7": hex_to_rgba("#4480c8"), # royal blue
    "8": hex_to_rgba("#2a5696"), # dark blue
    "W": hex_to_rgba("#ffffff"), # pure feather white
    "w": hex_to_rgba("#e4eaf4"), # soft white
    "v": hex_to_rgba("#b8c3d8"), # feather shadow
    "u": hex_to_rgba("#6e7a98"), # feather edge
    "M": hex_to_rgba("#e4f8ff"), # gem sparkle
    "m": hex_to_rgba("#58b8ff"), # bright mana blue
    "x": hex_to_rgba("#141822"), # interior helmet/collar shadow
    "X": hex_to_rgba("#202636"), # mid interior shadow
}

VALK_HELMET_ROWS = [
    "................",
    "..W..........W..",
    ".WwW..kkkk..WwW.",
    ".wwv.kSss1k.vww.",
    "kww1kSg44gSk1wwk",
    "ku2k1g4Mm4g1k2uk",
    "..k1ks6446sk1k..",
    "..k21kXXXXk12k..",
    "..k21kXXXXk12k..",
    "..k32kxXXxk23k..",
    "...k3.xxxx.3k...",
    "...kk......kk...",
    "................",
    "................",
    "................",
    "................",
]

VALK_CHEST_ROWS = [
    "................",
    ".kkkk......kkkk.",
    "kSss1kkXXkkSss1k",
    "ksww1kG44Gk1wwsk",
    "ksuv1g4Mm4g1vusk",
    "k1u1kg7bb7gk1u1k",
    ".k2kS47bb74Sk2k.",
    ".k2k14788741k2k.",
    "..kk14788741kk..",
    "...k1g4444g1k...",
    "...k2g5665g2k...",
    "...ks18bb81sk...",
    "...k12888821k...",
    "....ks1111sk....",
    ".....kkkkkk.....",
    "................",
]

VALK_LEGS_ROWS = [
    "................",
    "..kkkkkkkkkkkk..",
    "..kSg44Mm44gSk..",
    "..ks47888874sk..",
    "..k147bbbb741k..",
    "..k147bbbb741k..",
    "..k2G47kk74G2k..",
    "..k2s1kkkks12k..",
    "..kSw1k..kSw1k..",
    "..k1v2k..k1v2k..",
    "..k1s2k..k1s2k..",
    "..k213k..k213k..",
    "..k323k..k323k..",
    "..kG46k..kG46k..",
    "..kkkkk..kkkkk..",
    "................",
]

VALK_BOOTS_ROWS = [
    "................",
    "................",
    "................",
    "................",
    "..kkkk....kkkk..",
    "..ks1k....ks1k..",
    "..k12k....k12k..",
    "W.k12k....k12k.W",
    "Wwks4Gk..kG4skwW",
    "wwkSss1kk1ssSkww",
    "uukSsg4kk4gsSuuk",
    ".kk1234kk4321kk.",
    "..kkkkk..kkkkk..",
    "................",
    "................",
    "................",
]

# ==============================================================================
# 4. PHOENIX MAGE ARMOR
# ==============================================================================
PHOENIX_ARMOR_PAL = {
    "k": hex_to_rgba("#1e0806"),
    "C": hex_to_rgba("#dc5424"), # bright crimson highlight
    "c": hex_to_rgba("#b8341a"), # crimson body
    "1": hex_to_rgba("#8a2014"), # mid crimson
    "2": hex_to_rgba("#5a120c"), # dark crimson
    "3": hex_to_rgba("#2e0806"), # deep shadow crimson
    "F": hex_to_rgba("#fff2b0"), # flame white-yellow tip
    "f": hex_to_rgba("#ffd064"), # bright yellow flame
    "A": hex_to_rgba("#ff9e34"), # amber flame
    "a": hex_to_rgba("#ea6a16"), # fiery orange
    "0": hex_to_rgba("#b83a0c"), # deep flame base
    "G": hex_to_rgba("#ffe6a6"), # gold glint
    "g": hex_to_rgba("#f2be52"), # bright gold
    "4": hex_to_rgba("#d08a24"), # gold midtone
    "5": hex_to_rgba("#8e5410"), # gold shadow
    "6": hex_to_rgba("#4e2a06"), # deep gold rim
    "R": hex_to_rgba("#ffa070"), # ruby glint
    "r": hex_to_rgba("#f04a24"), # vivid ruby
    "e": hex_to_rgba("#a81810"), # deep ruby
    "x": hex_to_rgba("#160806"), # interior hood shadow
    "X": hex_to_rgba("#260e0a"), # mid hood shadow
}

PHOENIX_HELMET_ROWS = [
    ".......ff.......",
    "......fAFf......",
    ".....kAaa0k.....",
    "....kC1111Ck....",
    "...kCcg44gcCk...",
    "..kC1g4Rr4g1Ck..",
    "..kc2k6446k2ck..",
    "..kc2kXXXXk2ck..",
    "..k13kXXXXk31k..",
    "..k13kxXXxk31k..",
    ".kAa2kxxxxk2aAk.",
    ".kfa1kk..kk1afk.",
    "..kkkk....kkkk..",
    "................",
    "................",
    "................",
]

PHOENIX_CHEST_ROWS = [
    "................",
    ".kkkk......kkkk.",
    "kCc12kkXXkkCc12k",
    "kfaA1kG44Gk1Aafk",
    "kfA01g4Rr4g10Afk",
    "kc12kg4rre4gk21k",
    ".k2kC4aAAa4Ck2k.",
    ".k2kc4aAAa4ck2k.",
    "..kk14aff041kk..",
    "...k1g4444g1k...",
    "...k2g5665g2k...",
    "...kc1aAAa1ck...",
    "...k12afFa21k...",
    "....kc1aa1ck....",
    ".....kkkkkk.....",
    "................",
]

PHOENIX_LEGS_ROWS = [
    "................",
    "..kkkkkkkkkkkk..",
    "..kCg44Rr44gCk..",
    "..kc4aAAaAA4ck..",
    "..k14affffff41k.",
    "..k14aAffAA41k..",
    "..k2G40kk04G2k..",
    "..k2c1kkkkc12k..",
    "..kCA1k..k1ACk..",
    "..kc02k..k20ck..",
    "..kc12k..k21ck..",
    "..k123k..k321k..",
    "..k233k..k332k..",
    "..kG46k..kG46k..",
    "..kkkkk..kkkkk..",
    "................",
]

PHOENIX_BOOTS_ROWS = [
    "................",
    "................",
    "................",
    "................",
    "..kkkk....kkkk..",
    "..kc1k....kc1k..",
    "..k12k....k12k..",
    "f.k12k....k12k.f",
    "fAkc4Gk..kG4ckAf",
    "aAkCc12kk21cCkAa",
    "00kCca4kk4acK00k",
    ".kk1234kk4321kk.",
    "..kkkkk..kkkkk..",
    "................",
    "................",
    "................",
]

def slider_codex():
    return render_icon(SLIDER_ROWS, SLIDER_PAL)

def valkyrie_grimoire():
    return render_icon(VALKYRIE_ROWS, VALKYRIE_PAL)

def solar_codex():
    return render_icon(SOLAR_ROWS, SOLAR_PAL)

def zanite_focus_pendant():
    return render_icon(ZANITE_PENDANT_ROWS, ZANITE_PENDANT_PAL)

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
