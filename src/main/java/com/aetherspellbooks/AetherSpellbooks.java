package com.aetherspellbooks;

import com.aetherspellbooks.config.ASConfig;
import com.aetherspellbooks.registry.ASBiomeModifiers;
import com.aetherspellbooks.registry.ASBlocks;
import com.aetherspellbooks.registry.ASCreativeTabs;
import com.aetherspellbooks.registry.ASEntities;
import com.aetherspellbooks.registry.ASItems;
import com.aetherspellbooks.registry.ASParticles;
import com.aetherspellbooks.registry.ASSpells;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(AetherSpellbooks.MODID)
public class AetherSpellbooks {
    public static final String MODID = "aether_spellbooks";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AetherSpellbooks(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();

        ASBlocks.register(modBus);
        ASItems.register(modBus);
        ASEntities.register(modBus);
        ASSpells.register(modBus);
        ASParticles.register(modBus);
        ASCreativeTabs.register(modBus);
        ASBiomeModifiers.register(modBus);
        modBus.addListener(ASEntities::onAttributeCreation);
        modBus.addListener(ASEntities::onSpawnPlacements);
        modBus.addListener(ASCreativeTabs::addToVanillaTabs);

        context.registerConfig(ModConfig.Type.COMMON, ASConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
