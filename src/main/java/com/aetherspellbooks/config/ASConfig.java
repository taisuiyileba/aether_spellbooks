package com.aetherspellbooks.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ASConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue AETHER_EXCLUSIVE_SPELLS;
    public static final ModConfigSpec.DoubleValue AERCLOUD_DURATION_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue FIRE_MINION_DAMAGE;
    public static final ModConfigSpec.IntValue MOA_DISMOUNT_UNSUMMON_SECONDS;
    public static final ModConfigSpec.DoubleValue PENDANT_MIN_SPELL_POWER;
    public static final ModConfigSpec.DoubleValue PENDANT_MAX_SPELL_POWER;
    public static final ModConfigSpec.IntValue VALKYRIE_SORCERESS_SPAWN_WEIGHT;
    public static final ModConfigSpec.IntValue SOLAR_ACOLYTE_SPAWN_WEIGHT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("loot");
        AETHER_EXCLUSIVE_SPELLS = builder
                .comment("If true, spells from this mod only drop from Aether loot (dungeons, Aether mobs) and never from Iron's Spells' generic loot.")
                .define("aetherExclusiveSpells", true);
        builder.pop();

        builder.push("spells");
        AERCLOUD_DURATION_MULTIPLIER = builder
                .comment("Multiplier for how long Aercloud Step platforms last.")
                .defineInRange("aercloudDurationMultiplier", 1.0, 0.1, 10.0);
        FIRE_MINION_DAMAGE = builder
                .comment("Base melee damage of summoned Fire Minions (scaled by fire spell power).")
                .defineInRange("fireMinionDamage", 6.0, 0.0, 100.0);
        MOA_DISMOUNT_UNSUMMON_SECONDS = builder
                .comment("Seconds a summoned Moa waits without a rider before vanishing.")
                .defineInRange("moaDismountUnsummonSeconds", 5, 1, 600);
        builder.pop();

        builder.push("accessories");
        PENDANT_MIN_SPELL_POWER = builder
                .comment("Spell power bonus of the Zanite Focus Pendant when fully repaired (0.05 = +5%).")
                .defineInRange("pendantMinSpellPower", 0.05, 0.0, 10.0);
        PENDANT_MAX_SPELL_POWER = builder
                .comment("Spell power bonus of the Zanite Focus Pendant just before it is fully worn.")
                .defineInRange("pendantMaxSpellPower", 0.20, 0.0, 10.0);
        builder.pop();

        builder.comment("The spellcasters live in their shrines (the Valkyrie Sanctum and the Solar Altar) and appear when a shrine",
                "generates, like Iron's Spells' casters. These weights can additionally let them spawn naturally in Aether biomes,",
                "in the Aether's surface monster category (for reference, the Aether gives Whirlwinds 3, Blue Swets 6 and Aechor",
                "Plants 7). 0 = shrines only. Applied when a world loads.")
                .push("mobs");
        VALKYRIE_SORCERESS_SPAWN_WEIGHT = builder
                .comment("Natural spawn weight of the Valkyrie Sorceress on the Aether's surface. Since v1.6 they live in Valkyrie Sanctums, so this is 0 by default (it was 3).")
                .defineInRange("valkyrieSorceressNaturalSpawnWeight", 0, 0, 100);
        SOLAR_ACOLYTE_SPAWN_WEIGHT = builder
                .comment("Natural spawn weight of the Solar Acolyte on the Aether's surface. Since v1.6 they live in Solar Altars, so this is 0 by default (it was 2).")
                .defineInRange("solarAcolyteNaturalSpawnWeight", 0, 0, 100);
        builder.pop();

        SPEC = builder.build();
    }

    public static int valkyrieSorceressWeight() {
        return SPEC.isLoaded() ? VALKYRIE_SORCERESS_SPAWN_WEIGHT.get() : VALKYRIE_SORCERESS_SPAWN_WEIGHT.getDefault();
    }

    public static int solarAcolyteWeight() {
        return SPEC.isLoaded() ? SOLAR_ACOLYTE_SPAWN_WEIGHT.get() : SOLAR_ACOLYTE_SPAWN_WEIGHT.getDefault();
    }

    public static boolean aetherExclusive() {
        // Config may be queried before it is loaded (e.g. during registry scans); default to exclusive.
        return !SPEC.isLoaded() || AETHER_EXCLUSIVE_SPELLS.get();
    }
}
