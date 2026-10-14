package com.aetherspellbooks.gametest;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.event.ASEvents;
import com.aetherspellbooks.item.ZaniteFocusPendantItem;
import com.aetherspellbooks.registry.ASItems;
import com.aetherspellbooks.registry.ASSpells;
import com.aetherteam.aether.inventory.menu.AccessoriesMenu;
import com.mojang.authlib.GameProfile;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The 1.20.1 Aether and standard accessory layouts both use Curios. Development only. */
@GameTestHolder(AetherSpellbooks.MODID)
@PrefixGameTestTemplate(false)
public class ASAccessoriesGameTests {
    @GameTest(template = "empty", batch = "aether_spellbooks_accessories", timeoutTicks = 40)
    public static void accessoriesEquipFromUseInBothLayouts(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "asb-right-click"));
        level.addNewPlayer(player);
        var inventory = CuriosApi.getCuriosInventory(player).orElseThrow(IllegalStateException::new);
        Map<Item, List<String>> expected = Map.of(
                ASItems.AMBROSIUM_RING.get(), List.of("aether_ring", "ring"),
                ASItems.ZANITE_FOCUS_PENDANT.get(), List.of("aether_pendant", "necklace"),
                ASItems.GRAVITITE_CASTING_GLOVES.get(), List.of("aether_gloves", "hands"),
                ASItems.VALKYRIE_MANTLE.get(), List.of("aether_cape", "back"));
        expected.forEach((item, slots) -> {
            for (int layout = 0; layout < slots.size(); layout++) {
                inventory.getCurios().values().forEach(handler -> {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        handler.getStacks().setStackInSlot(i, ItemStack.EMPTY);
                    }
                });
                String targetSlot = slots.get(layout);
                // Occupy every matching slot in the other layout so the actual event must use this one.
                for (String otherSlot : List.of(slots.get(1 - layout), "curio")) {
                    var occupied = inventory.getStacksHandler(otherSlot).orElseThrow(IllegalStateException::new).getStacks();
                    for (int i = 0; i < occupied.getSlots(); i++) {
                        occupied.setStackInSlot(i, new ItemStack(item));
                    }
                }
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
                var use = new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND);
                MinecraftForge.EVENT_BUS.post(use);
                helper.assertTrue(use.isCanceled()
                                && inventory.getStacksHandler(targetSlot).orElseThrow(IllegalStateException::new).getStacks().getStackInSlot(0).is(item)
                                && player.getMainHandItem().isEmpty(),
                        item + " should equip from use into " + targetSlot + " and leave the survival hotbar");
            }
        });
        level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "aether_spellbooks_accessories", timeoutTicks = 40)
    public static void accessoriesFitAetherAndCuriosSlots(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "asb-slots"));
        level.addNewPlayer(player);
        var inventory = CuriosApi.getCuriosInventory(player).orElseThrow(IllegalStateException::new);
        var menu = new AccessoriesMenu(1, player.getInventory());
        Map<Item, List<String>> expected = Map.of(
                ASItems.AMBROSIUM_RING.get(), List.of("aether_ring", "ring"),
                ASItems.ZANITE_FOCUS_PENDANT.get(), List.of("aether_pendant", "necklace"),
                ASItems.GRAVITITE_CASTING_GLOVES.get(), List.of("aether_gloves", "hands"),
                ASItems.VALKYRIE_MANTLE.get(), List.of("aether_cape", "back"));
        expected.forEach((item, slots) -> {
            var stack = new ItemStack(item);
            CuriosApi.getSlots(level).keySet().stream().filter(id -> !id.equals("curio")).forEach(id ->
                    helper.assertTrue(CuriosApi.isStackValid(new SlotContext(id, player, 0, false, true), stack)
                                    == slots.contains(id),
                            item + " should fit only " + slots + ", checked " + id));
            helper.assertTrue(menu.slots.stream().filter(s -> s.getClass().getSimpleName().equals("CurioSlot"))
                    .anyMatch(s -> s.mayPlace(stack)), item + " should be accepted by the actual Aether menu");
            var aetherContext = new SlotContext(slots.get(0), player, 0, false, true);
            var standardContext = new SlotContext(slots.get(1), player, 0, false, true);
            var aetherModifiers = CuriosApi.getAttributeModifiers(aetherContext, CuriosApi.getSlotUuid(aetherContext), stack);
            var standardModifiers = CuriosApi.getAttributeModifiers(standardContext, CuriosApi.getSlotUuid(standardContext), stack);
            helper.assertTrue(!aetherModifiers.isEmpty() && aetherModifiers.size() == standardModifiers.size(),
                    item + " should grant attributes in both layouts");
            aetherModifiers.forEach((attribute, modifier) -> helper.assertTrue(
                    standardModifiers.get(attribute).stream().anyMatch(other ->
                            Math.abs(other.getAmount() - modifier.getAmount()) < 1e-6
                                    && other.getOperation() == modifier.getOperation()),
                    item + " should grant the same " + attribute + " bonus in both layouts"));
            for (String slot : slots) {
                var handler = inventory.getStacksHandler(slot).orElseThrow(IllegalStateException::new);
                helper.assertTrue(handler.getStacks().isItemValid(0, stack), item + " should fit its inventory slot " + slot);
                inventory.setEquippedCurio(slot, 0, stack.copy());
                helper.assertTrue(handler.getStacks().getStackInSlot(0).is(item), item + " should equip in " + slot);
            }
        });
        helper.assertTrue(CuriosApi.isStackValid(new SlotContext("spellbook", player, 0, false, true),
                new ItemStack(ASItems.VALKYRIE_GRIMOIRE.get())), "spellbooks keep the Curios spellbook slot");
        level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "aether_spellbooks_accessories", timeoutTicks = 40)
    public static void aetherPendantWearsAndMantleSoftensFalls(GameTestHelper helper) {
        checkEffects(helper, false, "aether_pendant", "aether_cape");
    }

    @GameTest(template = "empty", batch = "aether_spellbooks_accessories", timeoutTicks = 40)
    public static void curiosPendantWearsAndMantleSoftensFalls(GameTestHelper helper) {
        checkEffects(helper, false, "necklace", "back");
    }


    @GameTest(template = "empty", batch = "aether_spellbooks_accessories", timeoutTicks = 60)
    public static void genericCuriosPendantWearsAndMantleSoftensFalls(GameTestHelper helper) {
        checkEffects(helper, false, "curio", "curio");
    }

    @GameTest(template = "empty", batch = "aether_spellbooks_accessories", timeoutTicks = 40)
    public static void unsupportedSlotsDoNotActivateEffects(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "asb-wrong-slots"));
        level.addNewPlayer(player);
        var inventory = CuriosApi.getCuriosInventory(player).orElseThrow(IllegalStateException::new);
        var pendant = new ItemStack(ASItems.ZANITE_FOCUS_PENDANT.get());
        // Force unsupported slots, as a pack could do by overriding its validators.
        inventory.setEquippedCurio("ring", 0, pendant);
        inventory.setEquippedCurio("hands", 0, new ItemStack(ASItems.VALKYRIE_MANTLE.get()));
        castEvent(player);
        helper.assertTrue(pendant.getDamageValue() == 0, "an inactive pendant must not wear");
        assertFall(helper, player, 1, "an inactive mantle must not soften falls");
        level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "aether_spellbooks_accessories", timeoutTicks = 40)
    public static void genericCuriosAccessoriesApplyAndRemoveLiveAttributes(GameTestHelper helper) {
        var player = tickingPlayer(helper);
        var inventory = CuriosApi.getCuriosInventory(player).orElseThrow(IllegalStateException::new);
        var attrs = List.of(AttributeRegistry.MANA_REGEN.get(), AttributeRegistry.SPELL_POWER.get(),
                AttributeRegistry.CAST_TIME_REDUCTION.get(), AttributeRegistry.ENDER_SPELL_POWER.get(),
                AttributeRegistry.HOLY_SPELL_POWER.get(), AttributeRegistry.MAX_MANA.get());
        double[] baseline = attrs.stream().mapToDouble(player::getAttributeValue).toArray();
        var items = List.of(ASItems.AMBROSIUM_RING.get(), ASItems.ZANITE_FOCUS_PENDANT.get(),
                ASItems.GRAVITITE_CASTING_GLOVES.get(), ASItems.VALKYRIE_MANTLE.get());
        for (int i = 0; i < items.size(); i++) {
            var stack = new ItemStack(items.get(i));
            var handler = inventory.getStacksHandler("curio").orElseThrow(IllegalStateException::new).getStacks();
            helper.assertTrue(handler.isItemValid(i, stack), items.get(i) + " must fit the generic curio slot");
            inventory.setEquippedCurio("curio", i, stack);
        }
        helper.runAfterDelay(4, () -> {
            double[] bonuses = {0.15, ZaniteFocusPendantItem.getSpellPowerBonus(
                    inventory.getStacksHandler("curio").orElseThrow(IllegalStateException::new).getStacks().getStackInSlot(1)), 0.10, 0.08, 0.10, 50};
            for (int i = 0; i < attrs.size(); i++) {
                double expected = baseline[i] + bonuses[i] * (i == 5 ? 1 : player.getAttribute(attrs.get(i)).getBaseValue());
                assertAttribute(helper, player, attrs.get(i), expected, "generic curio equipped attribute " + i);
            }
            for (int i = 0; i < items.size(); i++) inventory.setEquippedCurio("curio", i, ItemStack.EMPTY);
        });
        helper.runAfterDelay(8, () -> {
            for (int i = 0; i < attrs.size(); i++) assertAttribute(helper, player, attrs.get(i), baseline[i], "removed generic attribute " + i);
            helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            helper.succeed();
        });
    }

    private static void checkEffects(GameTestHelper helper, boolean aetherAccessories, String pendantSlot, String mantleSlot) {
        var player = tickingPlayer(helper);
        double baseline = player.getAttributeValue(AttributeRegistry.SPELL_POWER.get());
        double base = player.getAttribute(AttributeRegistry.SPELL_POWER.get()).getBaseValue();
        var equipped = equipEffects(helper, player, aetherAccessories, pendantSlot, mantleSlot,
                new ItemStack(ASItems.ZANITE_FOCUS_PENDANT.get()), new ItemStack(ASItems.VALKYRIE_MANTLE.get()));
        var pendant = equipped.pendant();
        helper.runAfterDelay(4, () -> {
            assertAttribute(helper, player, AttributeRegistry.SPELL_POWER.get(), baseline + base * ZaniteFocusPendantItem.getSpellPowerBonus(pendant), "fresh pendant");
            assertFall(helper, player, ASEvents.MANTLE_FALL_DAMAGE_MULTIPLIER, "equipped mantle");
            castEvent(player);
            helper.assertTrue(pendant.getDamageValue() == 1, "one cast must wear the pendant exactly once");
        });
        helper.runAfterDelay(8, () -> {
            assertAttribute(helper, player, AttributeRegistry.SPELL_POWER.get(), baseline + base * ZaniteFocusPendantItem.getSpellPowerBonus(pendant), "worn pendant");
            helper.assertTrue(player.getAttributeValue(AttributeRegistry.SPELL_POWER.get()) > baseline + base * com.aetherspellbooks.config.ASConfig.PENDANT_MIN_SPELL_POWER.get(),
                    "wear must increase the player's actual spell power");
            pendant.setDamageValue(pendant.getMaxDamage() - 2);
        });
        helper.runAfterDelay(12, () -> {
            assertAttribute(helper, player, AttributeRegistry.SPELL_POWER.get(), baseline + base * ZaniteFocusPendantItem.getSpellPowerBonus(pendant), "nearly depleted pendant");
            castEvent(player);
            helper.assertTrue(ZaniteFocusPendantItem.isDepleted(pendant), "the final cast must deplete the pendant");
        });
        helper.runAfterDelay(16, () -> {
            assertAttribute(helper, player, AttributeRegistry.SPELL_POWER.get(), baseline, "depletion must remove the player's modifier");
            pendant.setDamageValue(0);
        });
        helper.runAfterDelay(20, () -> {
            assertAttribute(helper, player, AttributeRegistry.SPELL_POWER.get(), baseline + base * ZaniteFocusPendantItem.getSpellPowerBonus(pendant), "repair must restore the player's modifier");
            equipped.unequip().run();
        });
        helper.runAfterDelay(24, () -> {
            assertAttribute(helper, player, AttributeRegistry.SPELL_POWER.get(), baseline, "unequipping must remove the player's modifier");
            assertFall(helper, player, 1, "unequipped mantle");
            helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            helper.succeed();
        });
    }

    /** A disconnected GameTest player has no connection tick; run vanilla doTick from its world tick. */
    private static FakePlayer tickingPlayer(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "asb-live-stats")) {
            @Override
            public void tick() {
                doTick();
            }
        };
        var pos = helper.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 2, 4.5));
        player.moveTo(pos.x, pos.y, pos.z, 0, 0);
        player.setNoGravity(true);
        helper.getLevel().addNewPlayer(player);
        return player;
    }

    private static void castEvent(FakePlayer player) {
        var spell = ASSpells.STONEBREAKER_SHARD.get();
        MinecraftForge.EVENT_BUS.post(new SpellOnCastEvent(player, spell.getSpellId(), 1, 10, spell.getSchoolType(), CastSource.SPELLBOOK));
    }

    private static void assertFall(GameTestHelper helper, FakePlayer player, float expected, String message) {
        var fall = new LivingFallEvent(player, 10, 1);
        MinecraftForge.EVENT_BUS.post(fall);
        helper.assertTrue(Math.abs(fall.getDamageMultiplier() - expected) < 1e-6, message);
    }

    private static void assertAttribute(GameTestHelper helper, FakePlayer player, net.minecraft.world.entity.ai.attributes.Attribute attribute, double expected, String message) {
        double actual = player.getAttributeValue(attribute);
        helper.assertTrue(Math.abs(actual - expected) < 1e-6, message + ": expected=" + expected + ", actual=" + actual);
    }

    private record EquippedEffects(ItemStack pendant, Runnable unequip) {}

    private static EquippedEffects equipEffects(GameTestHelper helper, FakePlayer player, boolean aetherAccessories,
                                         String pendantSlot, String mantleSlot, ItemStack pendant, ItemStack mantle) {
        var inventory = CuriosApi.getCuriosInventory(player).orElseThrow(IllegalStateException::new);
        int mantleIndex = pendantSlot.equals(mantleSlot) ? 1 : 0;
        inventory.setEquippedCurio(pendantSlot, 0, pendant);
        inventory.setEquippedCurio(mantleSlot, mantleIndex, mantle);
        return new EquippedEffects(pendant, () -> {
            inventory.setEquippedCurio(pendantSlot, 0, ItemStack.EMPTY);
            inventory.setEquippedCurio(mantleSlot, mantleIndex, ItemStack.EMPTY);
        });
    }
}
