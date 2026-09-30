package com.aetherspellbooks.registry;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.world.SpellcasterSpawnsModifier;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ASBiomeModifiers {
    public static final DeferredRegister<MapCodec<? extends BiomeModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, AetherSpellbooks.MODID);

    public static final DeferredHolder<com.mojang.serialization.MapCodec<? extends BiomeModifier>, com.mojang.serialization.MapCodec<SpellcasterSpawnsModifier>> SPELLCASTER_SPAWNS =
            SERIALIZERS.register("spellcaster_spawns", () -> SpellcasterSpawnsModifier.CODEC);

    public static void register(IEventBus bus) {
        SERIALIZERS.register(bus);
    }
}
