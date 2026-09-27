package com.aetherspellbooks.registry;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.world.SpellcasterSpawnsModifier;
import com.mojang.serialization.Codec;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ASBiomeModifiers {
    public static final DeferredRegister<Codec<? extends BiomeModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, AetherSpellbooks.MODID);

    public static final RegistryObject<Codec<SpellcasterSpawnsModifier>> SPELLCASTER_SPAWNS =
            SERIALIZERS.register("spellcaster_spawns", () -> SpellcasterSpawnsModifier.CODEC);

    public static void register(IEventBus bus) {
        SERIALIZERS.register(bus);
    }
}
