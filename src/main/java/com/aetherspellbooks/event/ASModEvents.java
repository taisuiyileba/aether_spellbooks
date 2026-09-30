package com.aetherspellbooks.event;

import com.aetherspellbooks.AetherSpellbooks;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;


@EventBusSubscriber(modid = AetherSpellbooks.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ASModEvents {
    public static final String ICE_MAGIC_IS_COLD_PACK = "ice_magic_is_cold";

    /**
     * Registers an optional built-in data pack (disabled by default) that adds Iron's Spells ice magic
     * to {@code aether:is_cold}, letting every ice spell hurt an unfrozen Sun Spirit.
     */
    @SubscribeEvent
    public static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        event.addPackFinders(AetherSpellbooks.id("datapacks/" + ICE_MAGIC_IS_COLD_PACK),
                PackType.SERVER_DATA, Component.translatable("pack.aether_spellbooks.ice_magic_is_cold"),
                PackSource.FEATURE, false, Pack.Position.TOP);
    }
}
