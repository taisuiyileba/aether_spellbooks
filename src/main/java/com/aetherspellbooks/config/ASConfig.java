package com.aetherspellbooks.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ASConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue AETHER_EXCLUSIVE_SPELLS;
    public static final ForgeConfigSpec.DoubleValue AERCLOUD_DURATION_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue FIRE_MINION_DAMAGE;
    public static final ForgeConfigSpec.IntValue MOA_DISMOUNT_UNSUMMON_SECONDS;
    public static final ForgeConfigSpec.DoubleValue PENDANT_MIN_SPELL_POWER;
    public static final ForgeConfigSpec.DoubleValue PENDANT_MAX_SPELL_POWER;
    public static final ForgeConfigSpec.IntValue VALKYRIE_SORCERESS_SPAWN_WEIGHT;
    public static final ForgeConfigSpec.IntValue SOLAR_ACOLYTE_SPAWN_WEIGHT;
    public static final ForgeConfigSpec.BooleanValue THROWN_TOOLS_DAMAGE_SLIDER;
    public static final ForgeConfigSpec.BooleanValue SPECTRAL_HAMMER_DAMAGE_SLIDER;
    public static final ForgeConfigSpec.DoubleValue SLIDER_HAMMER_BASE_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue SLIDER_HAMMER_DAMAGE_PER_LEVEL;
    public static final ForgeConfigSpec.BooleanValue TREASURE_VAULTS;
    public static final ForgeConfigSpec.BooleanValue KEY_PER_PARTICIPANT;
    public static final ForgeConfigSpec.BooleanValue RESET_VAULTS_ON_REVIVAL;
    public static final ForgeConfigSpec.BooleanValue GUARDIAN_REVIVAL;
    public static final ForgeConfigSpec.BooleanValue MODIFY_IRONS_VAULTS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("loot");
        AETHER_EXCLUSIVE_SPELLS = builder
                .comment("If true, spells from this mod only drop from Aether loot (dungeons, Aether mobs) and never from Iron's Spells' generic loot.")
                .define("aetherExclusiveSpells", true);
        builder.pop();

        builder.push("sliderCompatibility");
        THROWN_TOOLS_DAMAGE_SLIDER = builder
                .comment("Allow Iron's Spells' Throw to damage the Slider when its projectile carries a valid mining tool.")
                .define("thrownToolsDamageSlider", true);
        SPECTRAL_HAMMER_DAMAGE_SLIDER = builder
                .comment("Allow Spectral Hammer to target and strike the Slider while preserving dungeon restrictions.")
                .define("spectralHammerDamageSlider", true);
        SLIDER_HAMMER_BASE_DAMAGE = builder
                .comment("Level-one Spectral Hammer damage to the Slider, before spell power and resistance.")
                .defineInRange("hammerBaseDamage", 6.0, 0.0, 100.0);
        SLIDER_HAMMER_DAMAGE_PER_LEVEL = builder
                .comment("Additional Slider damage for each Spectral Hammer level after the first.")
                .defineInRange("hammerDamagePerLevel", 2.0, 0.0, 100.0);
        builder.pop();

        builder.comment("Dungeon rewards and rematches. Only bosses that belong to an Aether dungeon are affected; a summoned boss",
                "(spawn egg, /summon, other mods) keeps the Aether's behaviour.")
                .push("dungeons");
        TREASURE_VAULTS = builder
                .comment("When a dungeon boss dies, the still-locked Treasure Chest in its own room becomes a vault that each player",
                        "can unlock once with the dungeon key, ejecting all rolled rewards at once. Chests that were already",
                        "opened or filled by hand are left alone.")
                .define("treasureChestsBecomeVaults", true);
        KEY_PER_PARTICIPANT = builder
                .comment("If the boss room holds a vault, give each participant one dungeon key directly in their inventory instead",
                        "of dropping a single key. Offline players and full inventories keep pending keys for automatic delivery.")
                .define("keyPerParticipant", true);
        RESET_VAULTS_ON_REVIVAL = builder
                .comment("When a dungeon boss is revived, clear its room's vault reward history so each player can unlock it again.",
                        "Iron's Spells' original vaults are included only when modifyIronsSpellbooksVaults is also enabled.")
                .define("resetVaultsOnRevival", true);
        GUARDIAN_REVIVAL = builder
                .comment("A defeated dungeon boss leaves its soul behind. Using the matching relic (Sentry Core, Valkyrie's Oath, Solar",
                        "Ember) on it rebuilds the boss room and calls the boss back for a rematch.")
                .define("guardianRevival", true);
        MODIFY_IRONS_VAULTS = builder
                .comment("Apply dungeon vault enhancements to Iron's Spells' original vaults (Bone Vault, Cinderous Vault, Vault):",
                        "burst out all rolled rewards at once when opened, and refresh arena vaults when a boss (such as the Dead King)",
                        "is revived. Disabled by default.")
                .define("modifyIronsSpellbooksVaults", false);
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

    public static boolean thrownToolsDamageSlider() {
        return !SPEC.isLoaded() || THROWN_TOOLS_DAMAGE_SLIDER.get();
    }

    public static boolean spectralHammerDamageSlider() {
        return !SPEC.isLoaded() || SPECTRAL_HAMMER_DAMAGE_SLIDER.get();
    }

    public static double sliderHammerBaseDamage() {
        return SPEC.isLoaded() ? SLIDER_HAMMER_BASE_DAMAGE.get() : SLIDER_HAMMER_BASE_DAMAGE.getDefault();
    }

    public static double sliderHammerDamagePerLevel() {
        return SPEC.isLoaded() ? SLIDER_HAMMER_DAMAGE_PER_LEVEL.get() : SLIDER_HAMMER_DAMAGE_PER_LEVEL.getDefault();
    }

    public static boolean treasureVaults() {
        return !SPEC.isLoaded() || TREASURE_VAULTS.get();
    }

    public static boolean keyPerParticipant() {
        return !SPEC.isLoaded() || KEY_PER_PARTICIPANT.get();
    }

    public static boolean resetVaultsOnRevival() {
        return !SPEC.isLoaded() || RESET_VAULTS_ON_REVIVAL.get();
    }

    public static boolean guardianRevival() {
        return !SPEC.isLoaded() || GUARDIAN_REVIVAL.get();
    }

    public static boolean modifyIronsVaults() {
        return SPEC.isLoaded() && MODIFY_IRONS_VAULTS.get();
    }
}
