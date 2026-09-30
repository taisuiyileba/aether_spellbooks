package com.aetherspellbooks.registry;

import com.aetherspellbooks.AetherSpellbooks;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ASCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AetherSpellbooks.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.aether_spellbooks"))
            .icon(() -> ASItems.VALKYRIE_GRIMOIRE.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                ASItems.ITEMS.getEntries().forEach(item -> output.accept(item.get()));
                for (var entry : ASSpells.SPELLS.getEntries()) {
                    AbstractSpell spell = entry.get();
                    for (int level = spell.getMinLevel(); level <= spell.getMaxLevel(); level++) {
                        ItemStack scroll = new ItemStack(ItemRegistry.SCROLL.get());
                        ISpellContainer.createScrollContainer(spell, level, scroll);
                        output.accept(scroll);
                    }
                }
            })
            .build());

    /** Spawn eggs also go in the vanilla spawn egg tab. */
    public static void addToVanillaTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ASItems.VALKYRIE_SORCERESS_SPAWN_EGG);
            event.accept(ASItems.SOLAR_ACOLYTE_SPAWN_EGG);
        }
    }

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
