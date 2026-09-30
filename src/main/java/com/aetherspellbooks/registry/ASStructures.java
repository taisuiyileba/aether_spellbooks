package com.aetherspellbooks.registry;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.world.IslandShrineStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Structure types. The structures themselves (the Valkyrie Sanctum and the Solar Altar) are data:
 * {@code data/aether_spellbooks/worldgen/structure}, their templates are {@code data/aether_spellbooks/structures}.
 */
public class ASStructures {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, AetherSpellbooks.MODID);

    public static final RegistryObject<StructureType<IslandShrineStructure>> ISLAND_SHRINE =
            STRUCTURE_TYPES.register("island_shrine", () -> () -> IslandShrineStructure.CODEC);

    public static void register(IEventBus bus) {
        STRUCTURE_TYPES.register(bus);
    }
}
