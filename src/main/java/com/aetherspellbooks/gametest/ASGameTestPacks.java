package com.aetherspellbooks.gametest;

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

/** Provides standard Curios slots only during development checks. Excluded from the release jar. */
@Mod.EventBusSubscriber(modid = AetherSpellbooks.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ASGameTestPacks {
    private ASGameTestPacks() {}

    @SubscribeEvent
    public static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA
                || !(Boolean.getBoolean("asb.gametest") || "accessories".equals(System.getenv("ASB_RENDER_CHECK")))) {
            return;
        }
        var path = ModList.get().getModFileById(AetherSpellbooks.MODID).getFile()
                .findResource("datapacks/asb_gametest_world");
        var pack = Pack.readMetaAndCreate(AetherSpellbooks.MODID + ":asb_gametest_world",
                Component.literal("Aether Spellbooks development slots"), true,
                id -> new PathPackResources(id, path, true), PackType.SERVER_DATA, Pack.Position.TOP, PackSource.BUILT_IN);
        if (pack != null) {
            event.addRepositorySource(consumer -> consumer.accept(pack));
        }
    }
}
