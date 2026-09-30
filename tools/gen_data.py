"""
Generates the data-driven JSON of Aether Spellbooks: loot injection, tags, recipes, the optional
data pack, particle definitions and item/block models.
Usage:  python tools/gen_data.py      (overwrites the generated files)
"""
import json
import os

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
NS = "aether_spellbooks"


def w(path, obj):
    p = os.path.join(RES, path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def sp(*names):
    return [f"{NS}:{n}" for n in names]


# Spell tiers follow the Aether's dungeon progression
BRONZE = sp("aercloud_step", "stonebreaker_shard", "zephyr_blast")
SILVER = sp("thunder_crystal", "summon_moa", "valkyrie_lunge", "cloud_sentinels", "aether_whirlwind",
            "radiant_javelin", "thunderhead", "aerwhale_song")
GOLD = sp("frostbound_crystal", "summon_fire_minion", "solar_flare", "gravitite_surge", "icestone_meteor")


def scroll_pool(chance, spells, qmin, qmax, extra_conditions=()):
    return {"rolls": 1,
            "conditions": list(extra_conditions) + [{"condition": "minecraft:random_chance", "chance": chance}],
            "entries": [{"type": "minecraft:item", "name": "irons_spellbooks:scroll",
                         "functions": [{"function": "irons_spellbooks:randomize_spell",
                                        "quality": {"type": "minecraft:uniform", "min": qmin, "max": qmax},
                                        # "force" (inside spell_filter) lets the filter pick Aether-exclusive spells
                                        "spell_filter": {"spells": spells, "force": True}}]}]}


def item_pool(chance, item, count=None):
    entry = {"type": "minecraft:item", "name": item}
    if count:
        entry["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": count[0], "max": count[1]}}]
    return {"rolls": 1, "conditions": [{"condition": "minecraft:random_chance", "chance": chance}], "entries": [entry]}


KILLED_BY_PLAYER = [{"condition": "minecraft:killed_by_player"}]
TEMPLATE = "aether_arcane_upgrade_smithing_template"
ARMOR_PIECES = ("helmet", "chestplate", "leggings", "boots")
# Aether weapon -> (rune, spellcaster weapon), reforged with the same template
WEAPON_UPGRADES = {"flaming_sword": ("fire_rune", "sunfire_spellblade"), "lightning_sword": ("lightning_rune", "stormcaller_spellblade"),
                   "holy_sword": ("holy_rune", "hallowed_spellblade"), "vampire_blade": ("blood_rune", "sanguine_spellblade"),
                   "cloud_staff": ("evocation_rune", "aercloud_staff")}
SPELLBLADES = ["sunfire_spellblade", "stormcaller_spellblade", "hallowed_spellblade", "sanguine_spellblade"]
MAGE_SETS = {"valkyrie_mage": ("valkyrie", "irons_spellbooks:holy_rune"), "phoenix_mage": ("phoenix", "irons_spellbooks:fire_rune")}

INJECT = {
    "bronze_dungeon": ("aether:chests/dungeon/bronze/bronze_dungeon", [
        scroll_pool(0.25, BRONZE, 0.0, 0.5),
        item_pool(0.2, "irons_spellbooks:common_ink", (1, 2)),
        item_pool(0.08, f"{NS}:zanite_staff")]),
    "bronze_dungeon_reward": ("aether:chests/dungeon/bronze/bronze_dungeon_reward", [
        item_pool(0.30, f"{NS}:slider_codex"),
        scroll_pool(0.5, BRONZE, 0.2, 0.8)]),
    "silver_dungeon": ("aether:chests/dungeon/silver/silver_dungeon", [
        scroll_pool(0.2, BRONZE + SILVER, 0.0, 0.6),
        item_pool(0.15, "irons_spellbooks:uncommon_ink", (1, 2))]),
    "silver_dungeon_reward": ("aether:chests/dungeon/silver/silver_dungeon_reward", [
        item_pool(0.25, f"{NS}:valkyrie_grimoire"),
        scroll_pool(0.6, SILVER, 0.2, 0.9),
        item_pool(0.15, f"{NS}:ambrosium_ring"),
        item_pool(0.12, f"{NS}:valkyrie_mantle"),
        item_pool(0.35, f"{NS}:{TEMPLATE}"),
        item_pool(0.2, f"{NS}:valkyrie_scepter")]),
    "gold_dungeon_reward": ("aether:chests/dungeon/gold/gold_dungeon_reward", [
        item_pool(0.30, f"{NS}:solar_codex"),
        scroll_pool(0.7, GOLD, 0.3, 1.0),
        item_pool(0.15, f"{NS}:gravitite_casting_gloves"),
        item_pool(0.3, "irons_spellbooks:rare_ink", (1, 2)),
        item_pool(0.5, f"{NS}:{TEMPLATE}"),
        item_pool(0.2, f"{NS}:solar_scepter")]),
    "valkyrie": ("aether:entities/valkyrie", [
        scroll_pool(0.04, sp("thunder_crystal", "valkyrie_lunge", "radiant_javelin"), 0.0, 0.4, KILLED_BY_PLAYER)]),
    "zephyr": ("aether:entities/zephyr", [
        scroll_pool(0.05, sp("aercloud_step", "zephyr_blast", "aether_whirlwind", "aerwhale_song"), 0.0, 0.4, KILLED_BY_PLAYER)]),
}


def chance_with_looting(chance, per_level):
    return {"condition": "minecraft:random_chance_with_looting", "chance": chance, "looting_multiplier": per_level}


def counted(item, lo, hi, looting=None):
    functions = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
    if looting:
        functions.append({"function": "minecraft:looting_enchant", "count": {"type": "minecraft:uniform", "min": 0, "max": looting}})
    return {"type": "minecraft:item", "name": item, "functions": functions}


def mob_scroll_pool(chance, per_level, spells, qmin, qmax):
    pool = scroll_pool(chance, spells, qmin, qmax, KILLED_BY_PLAYER)
    pool["conditions"] = KILLED_BY_PLAYER + [chance_with_looting(chance, per_level)]
    return pool


# The spellcasting mobs. Since v1.6 they live in their shrines (two per shrine, like Iron's Spells' casters) instead
# of spawning everywhere, so each one is worth more: Victory Medals (so casters can also reach the Valkyrie Queen),
# Aether materials, arcane essence, a good chance at a scroll of their spells, Iron's Spells inks and runes and the
# upgrade template.
MOB_LOOT = {
    "valkyrie_sorceress": [
        {"rolls": 1, "conditions": KILLED_BY_PLAYER + [chance_with_looting(0.75, 0.1)],
         "entries": [{"type": "minecraft:item", "name": "aether:victory_medal"}]},
        {"rolls": 1, "entries": [counted("aether:ambrosium_shard", 1, 3, 1)]},
        {"rolls": 1, "entries": [counted("irons_spellbooks:arcane_essence", 1, 3, 1)]},
        {"rolls": 1, "conditions": KILLED_BY_PLAYER + [chance_with_looting(0.12, 0.03)],
         "entries": [{"type": "minecraft:item", "name": f"{NS}:{TEMPLATE}"}]},
        mob_scroll_pool(0.30, 0.05, sp("thunder_crystal", "valkyrie_lunge", "zephyr_blast", "gravitite_surge", "radiant_javelin", "thunderhead"), 0.2, 0.7),
        {"rolls": 1, "conditions": [chance_with_looting(0.30, 0.05)],
         "entries": [{"type": "minecraft:item", "name": "irons_spellbooks:uncommon_ink", "weight": 3},
                     {"type": "minecraft:item", "name": "irons_spellbooks:rare_ink", "weight": 1}]},
        {"rolls": 1, "conditions": [chance_with_looting(0.20, 0.05)],
         "entries": [{"type": "minecraft:item", "name": "irons_spellbooks:lightning_rune", "weight": 3},
                     {"type": "minecraft:item", "name": "irons_spellbooks:holy_rune", "weight": 2}]},
    ],
    "solar_acolyte": [
        {"rolls": 1, "conditions": KILLED_BY_PLAYER + [chance_with_looting(0.12, 0.03)],
         "entries": [{"type": "minecraft:item", "name": f"{NS}:{TEMPLATE}"}]},
        {"rolls": 1, "entries": [counted("aether:golden_amber", 2, 4, 1)]},
        {"rolls": 1, "entries": [counted("aether:ambrosium_shard", 1, 3, 1)]},
        {"rolls": 1, "entries": [counted("irons_spellbooks:arcane_essence", 1, 3, 1)]},
        mob_scroll_pool(0.30, 0.05, sp("solar_flare", "summon_fire_minion", "icestone_meteor"), 0.2, 0.7),
        {"rolls": 1, "conditions": [chance_with_looting(0.20, 0.05)],
         "entries": [{"type": "minecraft:item", "name": "irons_spellbooks:fire_rune"}]},
        {"rolls": 1, "conditions": [chance_with_looting(0.15, 0.04)],
         "entries": [{"type": "minecraft:item", "name": "irons_spellbooks:rare_ink"}]},
    ],
}


def weighted(item, weight, lo=1, hi=1):
    entry = {"type": "minecraft:item", "name": item, "weight": weight}
    if hi > 1:
        entry["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
    return entry


# The two shrines the spellcasters live in. Placement: see IslandShrineStructure (only on flat, solid island ground).
SHRINES = {
    "valkyrie_sanctum": dict(
        biomes=["aether:skyroot_meadow", "aether:skyroot_grove", "aether:skyroot_woodland", "aether:skyroot_forest"],
        spacing=14, separation=6, salt=918273645, footprint=7, slope=6,
        weathering=[("aether:angelic_stone", "aether:light_angelic_stone", 0.08), ("aether:holystone_bricks", "aether:mossy_holystone", 0.15)],
        loot=[
            {"rolls": {"type": "minecraft:uniform", "min": 4, "max": 7}, "entries": [
                weighted("aether:ambrosium_shard", 20, 2, 6), weighted("aether:zanite_gemstone", 12, 1, 3),
                weighted("irons_spellbooks:arcane_essence", 16, 2, 5), weighted("aether:white_apple", 8, 1, 2),
                weighted("aether:golden_feather", 3), weighted("irons_spellbooks:uncommon_ink", 10, 1, 2),
                weighted("irons_spellbooks:rare_ink", 4), weighted("irons_spellbooks:holy_rune", 6), weighted("irons_spellbooks:lightning_rune", 6),
                weighted("irons_spellbooks:divine_pearl", 2), weighted("aether:healing_stone", 4)]},
            scroll_pool(0.85, SILVER, 0.3, 0.9),
            scroll_pool(0.35, SILVER, 0.4, 1.0),
            {"rolls": 1, "conditions": [{"condition": "minecraft:random_chance", "chance": 0.45}], "entries": [
                weighted(f"{NS}:{TEMPLATE}", 5), weighted(f"{NS}:valkyrie_scepter", 3), weighted(f"{NS}:ambrosium_ring", 3),
                weighted(f"{NS}:valkyrie_mantle", 2), weighted(f"{NS}:valkyrie_grimoire", 1)]},
            item_pool(0.5, "aether:victory_medal", (1, 2)),
        ]),
    "solar_altar": dict(
        biomes=["aether:skyroot_meadow", "aether:skyroot_grove", "aether:skyroot_woodland", "aether:skyroot_forest"],
        spacing=17, separation=7, salt=564738291, footprint=7, slope=6,
        weathering=[("aether:hellfire_stone", "aether:light_hellfire_stone", 0.07)],
        loot=[
            {"rolls": {"type": "minecraft:uniform", "min": 4, "max": 7}, "entries": [
                weighted("aether:golden_amber", 20, 2, 6), weighted("aether:ambrosium_shard", 14, 2, 6),
                weighted("irons_spellbooks:arcane_essence", 16, 2, 5), weighted("minecraft:blaze_powder", 8, 1, 4),
                weighted("aether:enchanted_gravitite", 3), weighted("irons_spellbooks:rare_ink", 8), weighted("irons_spellbooks:epic_ink", 2),
                weighted("irons_spellbooks:fire_rune", 8), weighted("irons_spellbooks:ice_rune", 4), weighted("aether:healing_stone", 4)]},
            scroll_pool(0.85, GOLD, 0.4, 1.0),
            scroll_pool(0.35, GOLD + SILVER, 0.4, 1.0),
            {"rolls": 1, "conditions": [{"condition": "minecraft:random_chance", "chance": 0.45}], "entries": [
                weighted(f"{NS}:{TEMPLATE}", 5), weighted(f"{NS}:solar_scepter", 3), weighted(f"{NS}:zanite_focus_pendant", 3),
                weighted(f"{NS}:gravitite_casting_gloves", 2), weighted(f"{NS}:solar_codex", 1)]},
        ]),
}


def structures():
    for name, cfg in SHRINES.items():
        w(f"data/{NS}/tags/worldgen/biome/has_structure/{name}.json", {"replace": False, "values": cfg["biomes"]})
        w(f"data/{NS}/worldgen/structure/{name}.json", {
            "type": f"{NS}:island_shrine",
            "biomes": f"#{NS}:has_structure/{name}",
            # after trees and flowers, so the template's air clears any vegetation on the site
            "step": "top_layer_modification",
            "terrain_adaptation": "beard_thin",
            "spawn_overrides": {},
            "start_pool": f"{NS}:{name}",
            "footprint_radius": cfg["footprint"],
            "max_slope": cfg["slope"],
            "min_ground_depth": 2,
            # 0: the template's bottom layer replaces the top block of the ground
            "sink": 0})
        w(f"data/{NS}/worldgen/structure_set/{name}.json", {
            "structures": [{"structure": f"{NS}:{name}", "weight": 1}],
            "placement": {"type": "minecraft:random_spread", "salt": cfg["salt"], "spacing": cfg["spacing"], "separation": cfg["separation"],
                          # keep clear of the Aether's own silver and gold dungeons
                          "exclusion_zone": {"other_set": "aether:silver_and_gold_dungeons", "chunk_count": 3}}})
        w(f"data/{NS}/worldgen/template_pool/{name}.json", {
            "name": f"{NS}:{name}",
            "fallback": "minecraft:empty",
            "elements": [{"weight": 1, "element": {"element_type": "minecraft:single_pool_element", "location": f"{NS}:{name}",
                                                   "projection": "rigid", "processors": f"{NS}:{name}"}}]})
        w(f"data/{NS}/worldgen/processor_list/{name}.json", {"processors": [{"processor_type": "minecraft:rule", "rules": [
            {"input_predicate": {"predicate_type": "minecraft:random_block_match", "block": src, "probability": chance},
             "location_predicate": {"predicate_type": "minecraft:always_true"},
             "output_state": {"Name": dst}} for src, dst, chance in cfg["weathering"]]}]})
        w(f"data/{NS}/loot_tables/chests/{name}.json", {"type": "minecraft:chest", "pools": cfg["loot"]})


def mobs():
    for mob, pools in MOB_LOOT.items():
        w(f"data/{NS}/loot_tables/entities/{mob}.json", {"type": "minecraft:entity", "pools": pools})
    # spawn weights come from the config (see SpellcasterSpawnsModifier)
    w(f"data/{NS}/forge/biome_modifier/spellcaster_spawns.json",
      {"type": f"{NS}:spellcaster_spawns", "biomes": "#aether:is_aether"})


def loot():
    entries = []
    for name, (target, pools) in INJECT.items():
        w(f"data/{NS}/loot_tables/inject/{name}.json",
          {"type": "minecraft:chest" if "chests" in target else "minecraft:entity", "pools": pools})
        w(f"data/{NS}/loot_modifiers/{name}.json", {
            "type": "irons_spellbooks:append_loot",
            "conditions": [{"condition": "forge:loot_table_id", "loot_table_id": target}],
            "key": f"{NS}:inject/{name}"})
        entries.append(f"{NS}:{name}")
    w("data/forge/loot_modifiers/global_loot_modifiers.json", {"replace": False, "entries": entries})


def tags():
    def tag(path, values):
        w(path, {"replace": False, "values": values})

    tag("data/aether/tags/entity_types/slider_damaging_projectiles.json", sp("stonebreaker_shard"))
    tag("data/curios/tags/items/spellbook.json", sp("slider_codex", "valkyrie_grimoire", "solar_codex"))
    # Each accessory fits the Aether's slot and the matching default Curios slot
    for slot, items in {"ring": ["ambrosium_ring"], "aether_ring": ["ambrosium_ring"],
                        "necklace": ["zanite_focus_pendant"], "aether_pendant": ["zanite_focus_pendant"],
                        "hands": ["gravitite_casting_gloves"], "aether_gloves": ["gravitite_casting_gloves"],
                        "back": ["valkyrie_mantle"], "aether_cape": ["valkyrie_mantle"]}.items():
        tag(f"data/curios/tags/items/{slot}.json", sp(*items))
    for piece, plural in zip(ARMOR_PIECES, ("helmets", "chestplates", "leggings", "boots")):
        pieces = sp(*[f"{mage}_{piece}" for mage in MAGE_SETS])
        tag(f"data/irons_spellbooks/tags/items/armors/{plural}.json", pieces)
        tag(f"data/forge/tags/items/armors/{plural}.json", pieces)
    tag("data/forge/tags/items/tools/swords.json", sp(*SPELLBLADES))
    tag("data/minecraft/tags/items/swords.json", sp(*SPELLBLADES))
    tag("data/aether/tags/items/accessories.json", sp("ambrosium_ring", "zanite_focus_pendant", "gravitite_casting_gloves", "valkyrie_mantle"))
    tag("data/aether/tags/items/accessories_rings.json", sp("ambrosium_ring"))
    tag("data/aether/tags/items/accessories_pendants.json", sp("zanite_focus_pendant"))
    tag("data/aether/tags/items/accessories_gloves.json", sp("gravitite_casting_gloves"))
    tag("data/aether/tags/items/accessories_capes.json", sp("valkyrie_mantle"))


def recipes():
    def shapeless(name, ingredients):
        w(f"data/{NS}/recipes/{name}.json", {
            "type": "minecraft:crafting_shapeless", "category": "equipment",
            "ingredients": [{"item": i} for i in ingredients],
            "result": {"item": f"{NS}:{name}"}})

    essence = "irons_spellbooks:arcane_essence"
    shapeless("ambrosium_ring", ["aether:zanite_ring"] + ["aether:ambrosium_shard"] * 4 + [essence])
    shapeless("zanite_focus_pendant", ["aether:zanite_pendant", "aether:zanite_gemstone", "aether:zanite_gemstone", essence, essence])
    shapeless("gravitite_casting_gloves", ["aether:gravitite_gloves", "aether:enchanted_gravitite", essence, essence])
    shapeless("valkyrie_mantle", ["aether:valkyrie_cape", "aether:golden_feather", essence, essence])

    # Arcane upgrade: Aether armour + rune on a smithing table (the base item's enchantments and damage carry over)
    for mage, (aether_set, rune) in MAGE_SETS.items():
        for piece in ARMOR_PIECES:
            w(f"data/{NS}/recipes/{mage}_{piece}_smithing.json", {
                "type": "minecraft:smithing_transform",
                "template": {"item": f"{NS}:{TEMPLATE}"},
                "base": {"item": f"aether:{aether_set}_{piece}"},
                "addition": {"item": rune},
                "result": {"item": f"{NS}:{mage}_{piece}"}})
    for base, (rune, result) in WEAPON_UPGRADES.items():
        w(f"data/{NS}/recipes/{result}_smithing.json", {
            "type": "minecraft:smithing_transform",
            "template": {"item": f"{NS}:{TEMPLATE}"},
            "base": {"item": f"aether:{base}"},
            "addition": {"item": f"irons_spellbooks:{rune}"},
            "result": {"item": f"{NS}:{result}"}})
    w(f"data/{NS}/recipes/zanite_staff.json", {
        "type": "minecraft:crafting_shaped", "category": "equipment",
        "pattern": [" ZE", " SZ", "S  "],
        "key": {"Z": {"item": "aether:zanite_gemstone"}, "E": {"item": essence}, "S": {"item": "aether:skyroot_stick"}},
        "result": {"item": f"{NS}:zanite_staff"}})
    w(f"data/{NS}/recipes/gravitite_staff.json", {
        "type": "minecraft:crafting_shaped", "category": "equipment",
        "pattern": [" GR", " TG", "S  "],
        "key": {"G": {"item": "aether:enchanted_gravitite"}, "R": {"item": "irons_spellbooks:ender_rune"},
                "T": {"item": f"{NS}:zanite_staff"}, "S": {"item": "aether:skyroot_stick"}},
        "result": {"item": f"{NS}:gravitite_staff"}})
    # duplicating the template, like the vanilla ones
    w(f"data/{NS}/recipes/{TEMPLATE}.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["#S#", "#C#", "###"],
        "key": {"#": {"item": essence}, "S": {"item": f"{NS}:{TEMPLATE}"}, "C": {"item": "aether:enchanted_gravitite"}},
        "result": {"item": f"{NS}:{TEMPLATE}", "count": 2}})


def optional_pack():
    p = "datapacks/ice_magic_is_cold"
    w(f"{p}/pack.mcmeta", {"pack": {"pack_format": 15,
                                    "description": "Aether Spellbooks: Iron's Spells ice magic counts as cold damage against the Sun Spirit"}})
    w(f"{p}/data/aether/tags/damage_type/is_cold.json", {"replace": False, "values": ["irons_spellbooks:ice_magic"]})


def assets():
    a = f"assets/{NS}"
    for book in ["slider_codex", "valkyrie_grimoire", "solar_codex"]:
        tex = {"book": f"{NS}:item/spell_book_models/{book}", "particle": f"{NS}:item/spell_book_models/{book}"}
        w(f"{a}/models/item/{book}.json", {
            "parent": "minecraft:item/handheld",
            "loader": "forge:separate_transforms",
            "base": {"parent": "irons_spellbooks:item/template_spell_book_model", "textures": tex},
            "perspectives": {
                "gui": {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{book}"}},
                "head": {"parent": "irons_spellbooks:item/template_spell_book_model_open", "textures": tex}}})
    for item in ["ambrosium_ring", "zanite_focus_pendant", "gravitite_casting_gloves", "valkyrie_mantle"]:
        w(f"{a}/models/item/{item}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{item}"}})
    for item in [f"{mage}_{piece}" for mage in MAGE_SETS for piece in ARMOR_PIECES] + [TEMPLATE]:
        w(f"{a}/models/item/{item}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{item}"}})
    for blade in SPELLBLADES:
        w(f"{a}/models/item/{blade}.json", {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/{blade}"}})
    # staff models (3D) are written by tools/gen_weapon_models.py
    for egg in ["valkyrie_sorceress_spawn_egg", "solar_acolyte_spawn_egg"]:
        w(f"{a}/models/item/{egg}.json", {"parent": "minecraft:item/template_spawn_egg"})
    for cloud in ["cold", "blue", "golden"]:
        # Uses the Aether's own cloud model at runtime (not redistributed)
        w(f"{a}/blockstates/temporary_{cloud}_aercloud.json", {"variants": {"": {"model": f"aether:block/{cloud}_aercloud"}}})
    for particle in ["feather", "sky_sparkle", "cloud_puff", "gravity_mote"]:
        w(f"{a}/particles/{particle}.json", {"textures": [f"{NS}:{particle}_{i}" for i in range(4)]})
    # the thundercloud puff is the aercloud puff tinted slate-grey in code
    w(f"{a}/particles/storm_puff.json", {"textures": [f"{NS}:cloud_puff_{i}" for i in range(4)]})


if __name__ == "__main__":
    loot()
    mobs()
    structures()
    tags()
    recipes()
    optional_pack()
    assets()
    print("data written to", os.path.normpath(RES))
