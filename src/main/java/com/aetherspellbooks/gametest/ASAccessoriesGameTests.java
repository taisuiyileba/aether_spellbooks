package com.aetherspellbooks.gametest;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.registry.ASItems;
import com.aetherspellbooks.registry.ASSpells;
import com.mojang.authlib.GameProfile;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesAPI;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@GameTestHolder(AetherSpellbooks.MODID)
@PrefixGameTestTemplate(false)
public class ASAccessoriesGameTests {
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aetherAccessoriesGrantStatsAndPendantWears(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "asb-accessories"));
        player.moveTo(helper.absolutePos(new net.minecraft.core.BlockPos(4, 1, 4)), 0, 0);
        level.addNewPlayer(player);
        var inventory = AccessoriesCapability.get(player);
        helper.assertTrue(inventory != null, "Accessories inventory should exist");
        var ring = new ItemStack(ASItems.AMBROSIUM_RING.get());
        var pendant = new ItemStack(ASItems.ZANITE_FOCUS_PENDANT.get());
        var ringSlot = inventory.attemptToEquipAccessory(ring);
        var pendantSlot = inventory.attemptToEquipAccessory(pendant);
        helper.assertTrue(ringSlot != null && pendantSlot != null,
                "Aether slots must accept the ring and pendant; slots=" + inventory.getContainers().keySet());
        var modifiers = AccessoriesAPI.getAttributeModifiers(ringSlot.getStack(), ringSlot).getAttributeModifiers(true);
        helper.assertTrue(modifiers.get(AttributeRegistry.MANA_REGEN).stream().anyMatch(m -> Math.abs(m.amount() - 0.15) < 1e-6),
                "equipped ring should grant 15% mana regeneration");
        var spell = ASSpells.STONEBREAKER_SHARD.get();
        NeoForge.EVENT_BUS.post(new SpellOnCastEvent(player, spell.getSpellId(), 1, 10, spell.getSchoolType(), CastSource.SPELLBOOK));
        helper.assertTrue(pendantSlot.getStack().getDamageValue() == 1, "casting should wear an Accessories pendant exactly once");
        level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        helper.succeed();
    }

    /**
     * Each accessory belongs to exactly one of The Aether's accessory slots - the ones its accessory menu shows - and to
     * no Curios slot, so right clicking or shift-clicking it can never hide it in a slot the player cannot see. The
     * spellbooks keep their Curios spellbook slot.
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void accessoriesOnlyFitTheirAetherSlot(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "asb-slots"));
        level.addNewPlayer(player);
        var inventory = AccessoriesCapability.get(player);
        Map<Item, String> expected = Map.of(
                ASItems.AMBROSIUM_RING.get(), "aether:ring_slot",
                ASItems.ZANITE_FOCUS_PENDANT.get(), "aether:pendant_slot",
                ASItems.GRAVITITE_CASTING_GLOVES.get(), "aether:gloves_slot",
                ASItems.VALKYRIE_MANTLE.get(), "aether:cape_slot");
        var curios = CuriosApi.getCuriosInventory(player).orElseThrow();
        expected.forEach((item, slot) -> {
            var stack = new ItemStack(item);
            var types = AccessoriesAPI.getStackSlotTypes(player, stack).stream().map(t -> t.name()).toList();
            helper.assertTrue(types.equals(List.of(slot)), item + " should fit only " + slot + ", fits " + types);
            curios.getCurios().keySet().forEach(id -> helper.assertFalse(
                    CuriosApi.isStackValid(new SlotContext(id, player, 0, false, true), stack),
                    item + " must not fit the Curios slot " + id + ", which The Aether's menu does not show"));
            var equipped = inventory.attemptToEquipAccessory(stack);
            helper.assertTrue(equipped != null && equipped.slotName().equals(slot),
                    item + " should equip into " + slot + ", went to " + (equipped == null ? "nothing" : equipped.slotName()));
        });
        helper.assertTrue(CuriosApi.isStackValid(new SlotContext("spellbook", player, 0, false, true),
                new ItemStack(ASItems.VALKYRIE_GRIMOIRE.get())), "spellbooks keep the Curios spellbook slot");
        level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        helper.succeed();
    }
}
