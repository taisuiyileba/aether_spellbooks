package com.aetherspellbooks.world;

import com.aetherspellbooks.config.ASConfig;
import com.aetherspellbooks.registry.ASBiomeModifiers;
import com.aetherspellbooks.registry.ASEntities;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

/**
 * Adds the Aether spellcasters to the given biomes, with spawn weights read from the config
 * (so pack makers can tune or disable them without a data pack).
 */
public record SpellcasterSpawnsModifier(HolderSet<Biome> biomes) implements BiomeModifier {
    public static final MapCodec<SpellcasterSpawnsModifier> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(SpellcasterSpawnsModifier::biomes)
    ).apply(instance, SpellcasterSpawnsModifier::new));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase == Phase.ADD && biomes.contains(biome)) {
            addSpawn(builder, ASEntities.VALKYRIE_SORCERESS.get(), ASConfig.valkyrieSorceressWeight());
            addSpawn(builder, ASEntities.SOLAR_ACOLYTE.get(), ASConfig.solarAcolyteWeight());
        }
    }

    private static void addSpawn(ModifiableBiomeInfo.BiomeInfo.Builder builder, EntityType<?> type, int weight) {
        if (weight > 0) {
            builder.getMobSpawnSettings().addSpawn(type.getCategory(), new MobSpawnSettings.SpawnerData(type, weight, 1, 1));
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return ASBiomeModifiers.SPELLCASTER_SPAWNS.get();
    }
}
