package com.aetherspellbooks;

import com.aetherspellbooks.config.ASConfig;
import com.aetherspellbooks.registry.ASBiomeModifiers;
import com.aetherspellbooks.registry.ASBlocks;
import com.aetherspellbooks.registry.ASCreativeTabs;
import com.aetherspellbooks.registry.ASEntities;
import com.aetherspellbooks.registry.ASItems;
import com.aetherspellbooks.registry.ASParticles;
import com.aetherspellbooks.registry.ASSpells;
import com.aetherspellbooks.registry.ASStructures;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import org.slf4j.Logger;

@Mod(AetherSpellbooks.MODID)
public class AetherSpellbooks {
    public static final String MODID = "aether_spellbooks";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AetherSpellbooks(IEventBus modBus, ModContainer context) {


        ASBlocks.register(modBus);
        com.aetherspellbooks.registry.ASArmorMaterials.MATERIALS.register(modBus);
        ASItems.register(modBus);
        ASEntities.register(modBus);
        ASSpells.register(modBus);
        ASParticles.register(modBus);
        ASCreativeTabs.register(modBus);
        ASBiomeModifiers.register(modBus);
        ASStructures.register(modBus);
        modBus.addListener(com.aetherspellbooks.compat.AccessoriesCompat::setup);
        modBus.addListener(ASEntities::onAttributeCreation);
        modBus.addListener(ASEntities::onSpawnPlacements);
        modBus.addListener(ASCreativeTabs::addToVanillaTabs);

        context.registerConfig(ModConfig.Type.COMMON, ASConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
