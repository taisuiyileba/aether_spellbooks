package com.aetherspellbooks.event;

import com.aetherspellbooks.AetherSpellbooks;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Path;

@Mod.EventBusSubscriber(modid = AetherSpellbooks.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
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
        Path path = ModList.get().getModFileById(AetherSpellbooks.MODID).getFile().findResource("datapacks/" + ICE_MAGIC_IS_COLD_PACK);
        Pack pack = Pack.readMetaAndCreate(
                AetherSpellbooks.MODID + ":" + ICE_MAGIC_IS_COLD_PACK,
                Component.translatable("pack.aether_spellbooks.ice_magic_is_cold"),
                false,
                id -> new PathPackResources(id, path, true),
                PackType.SERVER_DATA,
                Pack.Position.TOP,
                // FEATURE packs are not enabled automatically, so this stays opt-in
                PackSource.FEATURE);
        if (pack != null) {
            event.addRepositorySource(consumer -> consumer.accept(pack));
        }
    }
}
