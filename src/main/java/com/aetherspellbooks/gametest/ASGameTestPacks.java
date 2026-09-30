package com.aetherspellbooks.gametest;

import com.aetherspellbooks.AetherSpellbooks;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/** Vanilla's dedicated GameTest server bakes only the flat preset, discarding mod dimensions. */
@EventBusSubscriber(modid = AetherSpellbooks.MODID)
public final class ASGameTestPacks {
    @SubscribeEvent
    public static void addPack(AddPackFindersEvent event) {
        if (Boolean.getBoolean("asb.gametest")) {
            event.addPackFinders(AetherSpellbooks.id("datapacks/asb_gametest_world"), PackType.SERVER_DATA,
                    Component.literal("Aether Spellbooks GameTest world"), PackSource.BUILT_IN, true, Pack.Position.TOP);
        }
    }
}
